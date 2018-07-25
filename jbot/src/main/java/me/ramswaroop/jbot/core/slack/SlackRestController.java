package me.ramswaroop.jbot.core.slack;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ProcessBuilder.Redirect;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.request.WebRequest;

import me.ramswaroop.jbot.core.slack.models.Action;
import me.ramswaroop.jbot.core.slack.models.Attachment;
import me.ramswaroop.jbot.core.slack.models.Confirm;
import me.ramswaroop.jbot.core.slack.models.Payload;
import me.ramswaroop.jbot.core.slack.models.RichMessage;

@RestController
@RequestMapping("/slack")
public class SlackRestController {

	@Autowired
	SlackConfig slackConfig;

	private static final Logger logger = LoggerFactory.getLogger(SlackRestController.class);

	@RequestMapping(consumes = {
			MediaType.APPLICATION_FORM_URLENCODED_VALUE }, method = RequestMethod.POST, value = "/receive/message")
	public ResponseEntity<RichMessage> receiveInteractiveMessage(WebRequest request) {
		String payloadJson = request.getParameter("payload");
		Payload payload = JsonUtil.deSerialize(payloadJson, Payload.class);
		logger.info(payloadJson);
		String response_text;
		if (payload.getActions()[0].getName().equals("approve")) {
			response_text = approve(payload.getUser().getName(), payload.getActions()[0].getValue());
		} else if (payload.getActions()[0].getName().equals("reject")) {
			response_text = reject(payload.getUser().getName(), payload.getActions()[0].getValue());
		} else {
			response_text = "I dont know what to do with this.";
		}
		RichMessage message = new RichMessage();
		message.setText(response_text);
		message.setResponseType("in_channel");
		sendDeploymentMessage(message);
		return new ResponseEntity<RichMessage>(message, HttpStatus.OK);
	}

	private String approve(String user, String value) { // post to GoCD
		String url = "https://" + user + ":" + slackConfig.getP() + "@localhost:8154/go/run/" + value
				+ "/BusinessApproval";
		ProcessBuilder pb = new ProcessBuilder("curl", "-XPOST", url, "-k", "-H", "Confirm: true");
		try {
			Process process = pb.start();
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			BufferedReader readerErr = new BufferedReader(new InputStreamReader(process.getErrorStream()));
			StringBuilder builder = new StringBuilder();
			String line = null;
			while (process.isAlive()) {
				Thread.sleep(500);
			}
			while ((line = reader.readLine()) != null) {
				builder.append(line);
				builder.append(System.getProperty("line.separator"));
			}
			while ((line = readerErr.readLine()) != null) {
				builder.append(line);
				builder.append(System.getProperty("line.separator"));
			}
			String result = builder.toString();
			logger.info("curl -XPOST" + url + "-k -H Confirm: true");
			logger.info("GoCD Exit code: " + process.exitValue() + "; Response Text: \r\n" + result);
		} catch (IOException | InterruptedException e) {
			logger.error("Error calling GoCD", e);
		}
		// Send same responseText in response and to #Deployments channel
		String responseText = ":heavy_check_mark: " + user + " approved pipeline " + value
				+ " for deployment to production.\nPipeline Link: https://localhost:8154/go/pipelines/value_stream_map/"
				+ value;
		return responseText;
	}

	@RequestMapping(consumes = {
			MediaType.APPLICATION_FORM_URLENCODED_VALUE }, method = RequestMethod.POST, value = "/receive/slash-command")
	public ResponseEntity<RichMessage> receiveSlashCommand(@RequestParam("token") String token,
			@RequestParam("team_id") String teamId, @RequestParam("team_domain") String teamDomain,
			@RequestParam("channel_id") String channelId, @RequestParam("channel_name") String channelName,
			@RequestParam("user_id") String userId, @RequestParam("user_name") String userName,
			@RequestParam("command") String command, @RequestParam("text") String text,
			@RequestParam("response_url") String responseUrl) {

		if (token != slackConfig.getVerificationToken()) {
			throw new RuntimeException(String.format("Did not receive the expected token.  Expected [%s]  Actual [%s]",
					slackConfig.getVerificationToken(), token));
		}

		String response_text = null;
		String response_type = null;

		if (command == "/echo") {
			if (text == "help") {
				response_text = "I echo back what you tell me.\nTry typing `/echo hello` to see.";
				response_type = "ephemeral";
			} else {
				response_text = "token = " + token + ", team_id = " + teamId + ", team_domain = " + teamDomain
						+ ", channel_id = " + channelId + ", channel_name =" + channelName + "user_id" + userId
						+ "user_name" + userName + ", command = " + command + ", text = " + text + ", response_url ="
						+ responseUrl;
				response_type = "in_channel";
			}
		} else if (command == "/approve") {
			if (text == "help") {
				response_text = "I provide your business approval for a production release. Try typing `/approve -1` to see.";
				response_type = "ephemeral";
			} else {
				// * approve pipeline
				response_text = approveCommandLine(userName, text);
				response_type = "in_channel";
			}
		} else {
			response_text = "I'm afraid I don't know how to " + command + " yet.";
			response_type = "ephemeral";
		}
		RichMessage message = new RichMessage();
		message.setText(response_text);
		message.setType(response_type);
		return new ResponseEntity<RichMessage>(message, HttpStatus.OK);
	}

	private String approveCommandLine(String user, String value) {
		// post to GoCD
		String url = "https://" + user + ":" + slackConfig.getP()
				+ "@localhost:8154/go/run/" + value + "/BusinessApproval";
		ProcessBuilder pb = new ProcessBuilder("curl", "-XPOST", url, "-k", "-H", "Confirm: true");
		File log = new File("d:\\log.txt");
		pb.redirectErrorStream(true);
		pb.redirectOutput(Redirect.appendTo(log));
		try {
			pb.start();
		} catch (IOException e) {
			logger.error("Error calling GoCD", e);
		}
		// Send same responseText in response and to #Deployments channel
		String responseText = ":heavy_check_mark: " + user + " approved pipeline " + value
				+ " for deployment to production.\nPipeline Link: https://localhost:8154/go/pipelines/value_stream_map/"
				+ value;
		return responseText;
	}

	private String reject(String user, String value) {
		// Send same responseText in response and to #Deployments channel
		String responseText = ":x: " + user + " rejected pipeline " + value
				+ " for deployment to production.\nPipeline Link: https://localhost:8154/go/pipelines/value_stream_map/"
				+ value;
		return responseText;
	}

	@RequestMapping(method = RequestMethod.GET, value = "/webhook")
	public ResponseEntity<String> sendWebhookMessage(@RequestParam(value = "pipeline") String pipeline,
			@RequestParam(value = "instance") String instance, @RequestParam(value = "stage") String stage) {
		RichMessage msg = null;
		if (stage.equals("ProjectTeamRequest4Approval")) {
			// send approvalMessage to #business-approvals
			msg = getApprovalMessage(pipeline,instance);
			sendBusinessApprovalMessage(msg);
		} else if (stage.equals("BusinessApproval")) {
			// send deploymentNotificationMessage to #deployments
			msg = getDeploymentNotificationMessage(pipeline, instance);
			sendDeploymentMessage(msg);
		} else if (stage.equals("push")) {
			// send deployedNotificationMessage to #deployments
			msg = getDeployedNotificationMessage(pipeline, instance);
			sendDeploymentMessage(msg);
		}

		return new ResponseEntity<String>(HttpStatus.OK);
	}

	private void sendBusinessApprovalMessage(RichMessage msg) {
		RestTemplate restTemplate = new RestTemplate();
		try {
			restTemplate.postForEntity(slackConfig.getWebhookBusinessApproval(), msg.encodedMessage(), String.class);
		} catch (RestClientException e) {
			logger.error("Error posting to Slack Incoming Webhook: ", e);
		}

	}

	private void sendDeploymentMessage(RichMessage msg) {
		RestTemplate restTemplate = new RestTemplate();
		try {
			restTemplate.postForEntity(slackConfig.getWebhookDeployment(), msg.encodedMessage(), String.class);
		} catch (RestClientException e) {
			logger.error("Error posting to Slack Incoming Webhook: ", e);
		}

	}

	private RichMessage getDeploymentNotificationMessage(String pipeline, String instance) {
		return new RichMessage("##deployments", "PortfolioManagementApplication Deployment",
				":racing_car: A production deployment is in progress! Click here for details:\n https://localhost:8154/go/pipelines/value_stream_map/"
						+ pipeline + "/" + instance,
				":racing_car:", "in_channel", null);
	}

	private RichMessage getDeployedNotificationMessage(String pipeline, String instance) {
		return new RichMessage("##deployments", "PortfolioManagementApplication Deployment",
				":checkered_flag: A production deployment complete successfully! Click here for details:\n https://localhost:8154/go/pipelines/value_stream_map/"
						+ pipeline + "/" + instance,
				":checkered_flag:", "in_channel", null);
	}

	private RichMessage getApprovalMessage(String pipeline, String instance) {
		String approvalText = getApprovalFeatureList(instance);

		Action[] actions = new Action[] {
				new Action("1", "approve", "Approve", "button", pipeline + "/" + instance, "primary",
						new Confirm("Please confirm your decision", "Are you sure you want to approve this build?",
								"Yes", "No")),
				new Action("2", "reject", "Reject", "button", pipeline + "/" + instance, "danger", new Confirm(
						"Please confirm your decision", "Are you sure you want to reject this build?", "Yes", "No")) };
		Attachment[] attachments = new Attachment[] {
				new Attachment(approvalText, "You are unable to approve a deployment", "approve", "#3AA3E3", "default",
						null, null, null, null, null, null, null, null, null, null, null, null, null, actions) };
		return new RichMessage("##business-approvals", "PortfolioManagementApplication Deployment",
				":question: Please approve a deployment to production.", ":question:", "in_channel", attachments);

	}

	String getApprovalFeatureList(final String instance) {
		List<Integer> features = new ArrayList<>();
		StringBuilder sb = new StringBuilder("Click 'Approve' after successful application checkout.");

		String buildInstance = instance;
		if(instance.length() > 1) {
			buildInstance = instance.substring(instance.length() - 1, instance.length());
		}

		Integer valueOf = 0;
		try {
			valueOf = Integer.valueOf(buildInstance);
		} catch (NumberFormatException e) {
			valueOf = 0;
		}
		
		//Get the last digit.
		// Create unique feature numbers and descriptions
		switch (valueOf) {
		case 1:
			features.add(1234);
			features.add(1235);
			features.add(1236);
			break;
		case 2:
			features.add(1237);
			features.add(1238);
			break;
		case 3:
			features.add(1239);
			break;
		case 4:
			features.add(1240);
			features.add(1241);
			features.add(1242);
			features.add(1243);
			break;
		case 5:
			features.add(1244);
			features.add(1245);
			break;
		case 6:
			features.add(1246);
			break;
		case 7:
			features.add(1247);
			features.add(1248);
			break;
		case 8:
			features.add(1249);
			features.add(1250);
			break;
		case 9:
			features.add(1251);
			features.add(1252);
			features.add(1253);
			break;
		default:
			features.add(1249);
			break;
		}

		String featureText = buildFeatureList(features);
		sb.append(featureText);
		return sb.toString();
	}

	private Map<Integer, String> getFeatureAppUrlMap() {
		Map<Integer, String> appUrlMap = new HashMap<>();
		appUrlMap.put(1235, "http://35.241.60.10/portfolio/issuers");
		appUrlMap.put(1236, "http://35.241.60.10/security-rating/ratings");
		appUrlMap.put(1237, "http://35.241.60.10/portfolio/security/security-info");
		appUrlMap.put(1238, "http://35.241.60.10/portfolio/lot");
		appUrlMap.put(1239, "http://35.241.60.10/portfolio/security");
		appUrlMap.put(1240, "http://35.241.60.10/portfolio/security/bloomberg-pricing");
		appUrlMap.put(1241, "http://35.241.60.10/squirrelizer");
		appUrlMap.put(1243, "http://35.241.60.10/security-rating/ratings");
		appUrlMap.put(1244, "http://35.241.60.10/security-ratings/optimized");
		appUrlMap.put(1248,
				"data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD/2wCEAAkGBxATEhUQEhMVFRUWFxUbFxgVFxgWGBYaGBkaGRcWGBcdHiggGx0nGxcbIj0hJSorLjIuGh8zODMtQygtMCsBCgoKDg0OGhAQGjUlICUtLy0tLS0tLS8tLS0rLS0tLS8uLS0tLS0tLS0tLS8tLS0tLS0tLS0tLS0tKy0tLS01Lf/AABEIAPgAywMBIgACEQEDEQH/xAAbAAEAAgMBAQAAAAAAAAAAAAAABQYBBAcCA//EAEIQAAIBAwIEBAQDBQUGBwEAAAECAwAEERIhBQYxQRMiUWEHMnGRFFKBI0JiobEVM3KCwVOys8Lh8CRjg5Ki0dM1/8QAGQEBAQEBAQEAAAAAAAAAAAAAAAECBAMF/8QALREAAgIBAwIEBAcBAAAAAAAAAAECESEDEjEEQRNRcaEUMmGxIkJSgcHR8AX/2gAMAwEAAhEDEQA/AOo0pSuc5xSlKAUpSgFKUoBSlKA8GVckaht1ycY2z/Tenip+Zd/cf99jXxu7aEhjJgDqxLFQNgNXXA2A39h6VoSvw5QQ00C5Oc+MgO2cY83YE/erRaJRZ0PR1Pf5h0/7BrAuo841pkbkahsMA5+zL9xWjaixkjM0bQvGucyJJqRdIGcuGwMAD7A1smwgbzaQQQMEE4IwMY33GAPtTAwffxU/Mv3FelYEAg5B6Ed61JbK2AOoIobc6m0569yf4j962ogoUBcaQBpwcjHbB7ihD1SlKgFKUoBSlKAUpSgFKUoBSlKAUpSgFKUoBSlKAUpSgMPErAowDKwwwYAgg7EEHYiufcpcsWVxcXl01nCIBL4NvG0YwPBOJZdJ7ltv0IroVKqdFToonPdnPJ4PCrG3AjcGWdU0wI0aMoEOsDC6j177CtS343KTNxS6iWOHhweC3ghYsrznEbMGwM4BEYONstjpv0bNa95YRSxPBIgMcgYOvQHVuTt3zvnrneqpFUiicscv/iZZJuK2kT3DksDJMrEL5QkUdspIWNV/eY5yRsc10GKNVUIqhVUAKqgAKBsAANgB6VF8A5asrMEW0KxlvmbJZ274LsSce1S1RuyN2KUpUIKUpQClKUApSlAKUpQClKUApSlAKUpQCqdzbc8bjkd7U2q2qprLyYDRhRl/ELHfoSMDpirjXP8A4s8SkKQ8MgGqa6ZdQH5A2EU+gZ98+kbVY8ljyRfK/FOYL9GmhuYUjVtJMkaLltIbAURknAYfeukcHjuFhRbp0kmGdbRrpQ7nGB9MDtXy5e4NHZ28drHuEBJY9Xdjl3P1PbsMDtUjRssnYqqfETms2ECmPSZ5D+zDbqqrgvIwzkjHl+re1WieZEVpHIVEVmZj0VVGWP2FcKPMcd1xMXtxFLLGpBjt4lDvpT+5QrkbZ8598irFWIqzs54ssdoLy5XwQIlkkXqUJUHwx6nJwB6kVG8h8Zuby3e5nRUV5X8ALsfCG2G9cMCNXfBqk33E5+O3UdoiPBbREvOCctscanxsG6qq9iSd8bXDnLmKLhlqiQqokK6LaIDIUKMayOpVdvqcD1q12FdjV5359WxlS3jiWaQqWcFyojz8gJAPmO5x2GD3qe4bx6OezF/GkjIUZtCrqkyhKugUfMwKkbdcVybilibOykluG8TiN8wXSSGlhjY6pNQG4kfAU/4go6Guo8Nii4Zw5BK3lt4syEfvOx1MF92kbAHuKNIrSIE/FjhwGWjuV+qIP+farNyzx+G+iM0KyKoYqRIuk5AByNyCMHqDVG+G3DJbu5l41dAElnEQPQvjSzAflRP2Y98+m8j8WuaDbwi0jbTLOpLsDgxw5wfoXOVB9A/tRpXSFK6RrXPxIumnlhtLEXKxsQHieSTUASA5CpsDj+XU1I8I5i41M6huFpHGSNTvIY9I7nDeYnHoKrvw44lcRQeDY8NeRnIMtxK/hRHsuDp+RRsApPc966smrA1Y1YGrTnGe+M74z60eA6XY9GsUpWDApSlAKUpQClKUApSlAKUpQEJzZf30MSvZWy3DlsMGJyg7MEBBffY4ORt13xz5OA8wzXX9omKOKfACl2h0ounSAiZfTsT138x9a65SqnRpSog+UrPiEcb/ANoXCTSMwKhAMRjG66gq6s/TbHU1OUpUMle5/wCF3VzZPb2pXW7JqDNpDRg5ZdXbfGR3AIrd4BwUWlotrCQHCMDJjBaRgcyN3+Y9OwAFeuPG9CBrIQNID5kn1BXX0VlI0tn12qtHmPjq/PwdW947hP5AFjWldGs0THI/LK2Fv4RbxJXOqaT87dgM76QP5knvUCvIcl1eXNxxFyyk6bYROVwnVXGPl05xpPVtROQRn2/OXFV+bgc/6SM3+7EaDnjiHfgt193/APyq5Lkr3J/KkT8VleONltbKUjMhLNLMnQsSNzq8+2wAT1q0fE7hF7dx29tbLmNpczNkeTGAjsCd0GWbbuq18X57v8f/AMa7z7+Jj/hVgc+3g+bg92B028Qn/hUzYzdly4fZRwRJBEMRxqFUew7n3J3+pqhc48C4iOJJxCzhScsiqviaSsDgFdTKzDbByDuAS2R0ztH4lAfPw2+X/wBMn+oFD8U7QfPbXSe5RB/VhRJoitFs4FDdpCBeTLNMTklECIgOMRqABkD8xAJzW/VFb4scLwSPGLYJC6ANR7DOrAz6mpbk7nKDiHiBEaJ48Eo7BiyN0cEe4II7betZafJKZZKUpUIKUpQClKUApSlAKUpQClKUAqrc6c6xcPaFGjMrSZZlVgpSMHGsZ2LE5AU4zpO4qzTzIitJIwVEVmdj0VVGWP2FcKt0fi/EmeQ6ImJeQk4ENtHsFz0U4wM/mcn1rUVZqKvk7pbTpIiSxnUkiqyH8ysAVOPcEViK6jZnjWRGeMgOqsCyE7gOoOV/WuV85/ErUDbcP/ZxABTOPKxHTTAP3FxgB/m9ANjUj8LOTpoH/Hzgxlo2WOI/OVcgl5R2JwMKd98mm2lY24tnSKZ98f8AXpSuO8/8+fifEs7cRtbEaWdly0jAgh4zkaQpGx79fSolZErOxVnNVn4cX0s3DoJJmLOPETUTlmVHKqWPc4GM98VYp5kRGkkYKiKWZjsFUDJJ/SpRD6b01GuVcuc23d7xhDEWFuVkXwiTpWBQT4jj/aFtJz6sF6V1Sq1RWqPWs+ppljt1/nUTzLx2Kyt2uZN8bImcGSQg6UH2yT2AJrl/A+Hca4pG0/450QSMvmkkiBPzNoWMbqC2N+mMdqJFUbOwTWMbfPFG3+KNT/UVrWvBrWJzLFbwxyEEFo41RiD1BIHTYfaqRZfDe7H97xa6+kLSj7M0h++KvnD7QQxpEGkcIMapXMkjb5yzncnf/SjwRmxSlKhBSlKAUpSgFKUoBSlKAUpSgKD8YeN+FbLaKcNcEl/aJCC3/ubA+gaucvy9eK1vanCfjljdAzFEfqVSQ4+Zcg6d92Xviuo8zci/jb6K6llHgKiK8WDqbQWYKD00sW374z65Fj5g4LBeQmCYHGQyMuzxOPlkjbsw/wClbTSR6KSSK7yd8P4LMieUie4HRsYjiP8A5anq38bb+gFXKq7wNuLpMILtYJYFQ/8AikJV3IwE1R5+Y98DHfPrKceS4a2mW2IE5jYRk7YYj17H0PY4rLyzD5yc7+KHOmdfDrZtt1uJB39YEP8AJj/l9ap12lnFYII5Flu5pFMoAP8A4eJQxEYyMZLackdenQVeOQPh0ioZr+EFjskD4YIo6u+CQWPYdh9dvj8QOSDJcWqWVqiQvlZWiUKqkuMtJjoAnQ/WtppYPRNLBdeRrQxcPtUIwfCViPeTLn/eqh/FjmcySf2bASVVl8bTuZJMjRCPUAkZHdiB2NdWdcKVjwCFITPQEDC59ulcC5e4kthePNewvLPCX8upV0yknVKxI32JII/Nn0qR5szHmzrHIHKosYDrwbiXBlI/dx8sSn0XJ37kn2qyySKoLMQqqCWY7BQBkkn0Aqq8o8z3l8+v8EILUA/tXdiznHlEYKjUPfp715+K0ko4bL4YOGeMSkdos5Yn2yFB9iazTvJnl5OYc48wTcSuC0aSGKMEQoiszBSd5WUA+ZsD6DA7HN/5PvOLMsMMdjDZ2keATL4hcpnLBASGLscnURjJJqscjc1i1j/DWlnJcXczZdshQeyKMZOhR1JwMljtmuwWpkKIZQok0rrCElA2PMFJ3IzW5OsG5OsH1NKUrzPMUpSgFKUoBSlKAUpSgFKUoBSlKAVocb4xBaQtcXD6I1wOmSxPRVHcn/QntW/VP53sZjcWlyts95HB4mIFZVHjsVEcsmrbQBnfBwQOxNVclStk5wLjsN1CbhNUYUkSLMPDeIgBjrB2HlYNnOMEVu2t7DJGJo5EeIgnxFYFML8x1dNsHPpVQ+ItixijYwSSwvKJL1LbJed0jVYEPfwyyhSwGQApxnFRd7wK6Xh/7aBiJroTXVraBQywrHpitkUdgUjDackdd8VdqLSZ0Hh3EILhfEglSVc41IwYZ9D6V8YeOWrQ/iRMgh1OviOdClkYowBPXzA4x17VVrDgF+1vcyfsrSe6WGJIk+S0tkJBVdPWTS7n6nt23OVuSoLSTzK05jA8CaUhvDB6xrFnEbZydSjcHqD1UhSJ/jHFoLWIzzvpTIGQrMSW+VQoGSTURZcS4feThZLYrcKmtBdW4SQoDjUhYHYHt19qifiXNqMUE8kltZ4Z5Jo42lMko/uoQFB0gEazqwDtg56afLdqltw2bicFvI95pkxLcB2eQBsCcIxJVCvnKjfy4y2M0rArB0ZnGcEjOMgZGceoHXFeJtH92+nzhhobHnGPMNJ+YY61zv4c8KMt1/aLLIUWNgLi42lu5ZCNcirn9nEqgqqjsep3xocE4XdXN+slxBKJo7jxLm4lGlYlicmK1tOxVsLlhuQT0G5bfqNpfeFWPD7OVba3jSKW4V2CqCWdY8assckKM7AnGxx0qYDA5wQcbHBzg+h9DXOI+Vbqe6N3fvcR/iRjTatjwV1HTazMMsqaQh1KMai+ojq164Pwa2tY/Ct4ljUnJxklj01MxyWPuTSQZvUpSsmRSlKAUpSgFKUoBSlKAVmsVmgFKUoBWMVmlABQUpQHzuLiOMapHVFyBl2Cgk9FBPUn0r6EVwj4kcQmmv5o5chYW0Rp2RcAhx7uCG1dcEeldT5B5g/F2aPIw8WM+FLk9WUDS/1ZSp+pNacaVlapWWUHFM96guI8zQo/hRnXJtkAfKD0znGQfUdO9bl3xRUQEeckhfLghWbGNWSCBuK896Mb4+ZIE0JqqcQ5pZV8qPryd1TMZXoS2QTkEHHTOKkOD8fEpVHABcEqR0OOxGTgnrWfFjZFqRbom6UpXobFKUoDBpWaxQClKUApSlAKUpQCs1is0ApSlAKUpQClK0OM8TWCPWdJO2xbBPuB1bHp9ajdZI3SspPxf4IrRx3yjDpiN8D5lO6E/Rsj/NVG5RmUStGxbDjbDYwy7g/XGa6JxjiZvfFsQ8ISVFCluqOSCvmBKs3iFVwD1Nckt5jFKrYIKNuD6g4II+4rfzwaKnvg0XqW4kGuSSLJiwE848TB06gCCWOdzntprU4lI9wqyQuAWA1qGHUdCSME4OcZrb/EjNvofAdurnSWGMjSdOnvjfB22zUbxy0FvP4iAIG3GNuvUY9jiueCycS5+pIyXzosYkIEjnG/yaMDIxv9/U1t2PEo42Caj4g0FBjBYMARpYDJ64Gf9RVOtbmV5WKANhWGGwfKeoXVtqIzjNT8MulPEKgiPKgBSH3GNBJ3AD5Off6UnppGnDbydhRsgEjGQDg9RntXqqXynx+ZkGtFMa51FDqI2zj26fLv19qupU1uMrwdEJqSMUpStmxWKzWKAUpSgFKUoBSlKAVmsVmgFKUoBSla/EbsRRtITjA2z+Y7KPuRUbojdH2kdVxqIGSAMnGSegHqT6VzHnTjhe5ZVdgIRhV9CR55BsQTjGM56j6COv8AmCeO68YNh1c+UbglgAxAboDkkDtnqajrq4hUmdUjJd9/KMalbUWVSPLnfsPpWXlHk57keOOXAVo2iIxq8SNgdQzq1KV9PNuV9ar13Kzuzk5ZmZmwAPMxJY4GwGSelWlFRDK+tCshVhqIIGrUXBC5xgHp3wRUJxJLXH7NvNtnckH183T+temnKsG9GdYJnlLi4IFvMuVPkU6QRk/KH7+u/avtzI7N+yKkFTkd9ujfpjf71U4pHTdTgjB/UHINery6llYtI7MT6n/TpTwvxWiS6e9TcjYgcKGbUoHYZyx37DH9cVt3XHx4fhxoVJxrYkYbbfygdyAc5qCArbSwfYsQoJ/eOD9dPXFbcY8s9ZQhzI+68cuQgjWQooOcJ5d/UnrVy+EF3cPdzZd3jMWZCxLDWGHh7nocFq1eB8sWvlaUtJnRgnKxnV7KTkdiCa6/bW0ca6I0RFHRUUKPsK81qRbaiiKUXiKPpSlKAVismsUApSlAKUpQClKUArNYrNAKUoKAVHcxXCx20sjjKhTncA/Vc9SOtRfG+ZtOY7cBm7ud1H+EfvfXp9ap13BNcNqkaSRv1bHsANl/TFXbaDVoh+Icdt2DoQ0gIwAMAZD51auucKuCN/XNRXEuMySa/Iiq5JOFyc7b6j326jHetnivAZ4y+YgAm7DpgALtj82CNvevtwvgCSKrGUnIBKqvyg9i+cA/UVlbIKzyXh6asrYY99/Tf+Ve4LVn2RSxzjCjO56Cp+84UIdcTQqQR5Zi5AAOcNjHrgYxkHvUy1sgt4o8N4qrqUxMoYhSRv5cnJ6Ht+taequxt667Iok8LI2hwVO2x9+h+lTHCOGxOEZyfMWB3wBpwOwOPmG5rxx61myJpcEtsWUHHTIz6ZGftWrwy8Vcoya1bG2pl9iBg9x/TrWm3KNo3JuULRPXPBnaUgRGNQu4jK4bG2QT333BFSvAFjTCuviKf7yKURsjafLqYtkoSSCAGHTv1rweKRFgjNpCjCsRhCRkKCwIxsfpt9tjhvBpZpyY1VWKMxXQMOQRjURjQSH2O2TjtuOa5PBy7pPDPrGkSSFUT8OskoMeks6A4BGxwQc4GN+uANq6h/3v1/WuYWjk/KxkyyjQqYZTkDQAd23JIOOx67V0yNQqjJOAOrHcD+I+3rTSu2b0u7PdKrPFeaQPLAA38bdP8q9/qdqhk4zdk4EzknoAASfoMf0r6EOj1JK3j1OfU/6GlB0s+hf8VgiqT/Z/EJNyJf8APJp/kTX2htOJRHKhz7axID7aSf6VX00f1qzK66XPhui4UqO4PxZZsow0Sr8yHIP+IZ3x/SpGuaUXF0zthOM1uiKUpWTYpSlAKzWKzQClKUBof2La6i/gpkkk9cEncnTnG536VuxoFGFAUeigAfYV6oKAqXPNimmORRpJlzKynTqAQjLfm6KK5e17LHmAAHJyjBQNW4bPTznJxk+4rpvPLzeYFX8HwjgqAfN1JB/dddIIz6bZ3xzudAkrM51CQEdMYz1IxjfUSeg61hNbmczcd7tEpLNORqeZAskQ8reXBI3wm477n3G3Sp3h3BeHTmAxu8Lkld3y0vlOlQwx5srqwBgBW6Eg1B+ApQEpr8MArq+U6tio2y+2ThjtkdetfKPhLrdoyRsqdI8OUMbnbUhDklsemO3pWIySJptdzSPKnEJmlRIn0I7nzkqM5OCFbffHQDbNVpYyGHqD39R2xXc+Umv0L29yrskar4Mr4ycDBVj1bfcE77HUehPOOceBSxXPiSgDxsv5cY15OvpsN9/1r3jLsdUJdiZj5SnngVwAFco64xkq3t3x1OcbY9K6HwexEUCxaVQhcHw9umQDq65xvnPU1WvhfxTXbtbE+aE7Z7o5JH2bI/UVc6wobTKhRp8N4ZFAMRg79ycnqSB/M1XuaOItJJ+FiyQCAwH779k+g/r9KtF7ceHG8n5FZvrgZA+9Vvk2yyWuG3YEqufU7u313x966+nUYJ6j7cepx9VcnHRji+fQjn4TmUW0fmdRmV/3QT1A/hXp7nNWzhXCo4Fwu7H5nPU+w9B7VpXs5tWhSNFc3EwWRmJDb7kgAb7Z9gcZwCzL8oOPyfhGuyqsxdVWNEPkbUFdGbWVk0nUS4Kr5SDjBIa2vKapcfc103RR03vaz9v95lgNYqAXmGTOlkjGl2VyWYD9mgeXRgMWbfAQZzhmzgb+/wC2ptYUxKiO2iNnJBJRdU8jKDkImGXGxJAO4JI56Z20yQ4jYCUBlOmVd43HUH0Pqp6EU4TfiaMPjSwJV1/Kw6j6d/1r1wq8M0SzaQofdcMGyh+VsjYEjfTvj1PWong0mm8uYh0JLY9wRn/e/lXrFboNeWTl1H4erGvzYf8ADLBSlK8TpFKUoBWaxWaAUpSgFavELMyrgSyRnsUON/cd/vW1UfxDiaxsqnuQCeunPTb6/wAqxOUYq5EdVkgriC9I/CSSBwXARl3eXO+HJ+VV6nv5Rue+lzPy3aJAIwzePuwx5mmO5KkMQFHoSRgDvU1f28sZEwY5fIYjqmcaR9D396jbPwvHjeVtONQIzlZCR5VG2ff9Md68IzaeeWeDxKjT5DtPGhmhlVgn7MgknWrDPlBORgADb+RzVstOA20YUFBIV6NIAzDp02wOnptvity1tI4wVRQuSScdyT1Jr7V0KK7nrGCSyM1Xue+HeNaMwGWiOtfoPnH23/SrDWCoOxGQdiPUHqK0sGzjfJfFPAvI3OySfs364w+AD+jaTXZiK4fxuxME8kH5GOP8J3U/Yiuu8t3/AI9rFKTliuH/AMS+Vv5jP61uXmVn146pNvNj/Zsftuf5CtflYD8LHjvrJ+utqlcDodx3HqKhuX18JpLRuqMXjz+9G3Qj6Hr7mtxd6Tj9bOWUa14y801+/JI3yTEKIWCkuutiA2I99WkH949AcHr0NaNvJfCOIuo8R5D4gUIfBi0k6QA2k5YBdWTgPncivfGeFGYqwYKyLIADnSxfSPNg9NOsevnyMYrRj5bbKhpy0SyRtoAK6ljj0KpxsPMSxC4HlQbYOfJHUj2l3xTyZhjB0uXAYFdXyogbIOAQXJx+8q9ia+rXN8SyiPTvAoOFwurDSyBiTq0jKfKRkA75IGZuH3Znabxxo6pF5goIR0Gsj5h5g2PXPotfF+FThxJJdYjWQSEb+VVGyAk+5BJzkY71StqrJe8uliRpGOyj7nsPqTVe5PRmeadup2z7sdTf6fetHi1+93KscQOnPkHqe7t6DH2FW3h9msMaxL0HU+pPVvvXVKPg6dP5peyPmQl8Rr7l8sfdmxSlK4z6IpSlAKzWKzQClKUArw0SkglQSOhwK90qNWCO5hZhA2nIOxzjUFwQSSPoKr3BLGOYqMu2jJkJXy5BwEDnqcb59D1q4OgIIYAg9Qdwf0rEMKINKKFG+wGBk9T9a85aW6Vs85adytns0pSvU9BSlKAo3xE4Zl47hR8w0Nt3G6k/pkfpWx8NjIIpVZSE1Arn1IwfuAKtd3bJIuhxlTXuKJVAVQAB0ArV4oWe60eJ2Jk0vGdEseTG3bfqjeqmt6lSMnF2jM4qSpkRBx+PPhzgwyDqG+U+6t6fWpAXsOM+JHj11r/91m6tY5BpkQMPcdPoeo/SoeXlO3JyGdfbIP8AUV7Lwpc2vc538RDipez/AKNi85htk6N4h9E3/wDl0quXN5cXj6FG2dkX5V/ic9/qf0FT0PK1svza3+rYH8gKl4IURdKKFUdlGBXotXS0swVvzZ4y0NfWxqOl5I0OC8IWBTvqkb5m/wCVfb+tSVKVzSk5O2dsIRhHbFYFKUrJsUpSgFM0pQGc0zWKUBnNM1ilARfM/EZbe1luYhGxiVnIk1AEAHZdO+onAGdt60L3j09uY4p1ieWRHf8AZhkVCXhihjwWYkmSbBOeg6VNcRsY542hkBKNpyASpOlgw3HbKj6jatfiHBopp4bly2uE5CgjQ+DqXWCN9LgMMEbgZzVVFVGnPx91N3L4am2tRIviavPJNGAXQLjGjLaM5+ZTWTzDqEAgjDtOW0l30R+HGoMs4bBYxhiFBwNRII2IJ1Z+BwaJoJpmMDzBjGRhd5TcSRkru2p5AC23lCL1yTsTQWrSwzGQMYVCDXGHwM6lx5QEfMfUAnAHTY1aRcHmDj0j3JgTw20yiMqoJOFAM87Pq/ZorExgEZZlI+m5x69nTwo7dVLys4DOMgaI2k0AZALvp0jJA6ntitXgsVvb7RzHS2WcMnnkkfcuXxnvnT0y22M4r6cTnik8vjNHgAt5CwxmOQMvpINsNvjLbU7jufJuYWSTwpBFqU28bYYqPFZDNdEZ/cjgAfPrsa1LLm9iyCeLw1d7kLjOsLG0AiBX8xE+W9AhPY19rjhPD3dpWY5Yy+KdIy/ilSyu2jJUJEYwv5Sw671sDlu2ddSa01pc6SmlfD/FaCzIAMKVCKB6b5zmmBgkbC7MhkYD9mrlEP5ym0jj+HXlR66CehFbma+FnbJFGkSDCRqqqOuyjAye596+1ZMmc0zWKUANKUoBSlKAUpSgFKUoBSlKAUpSgFKUoBSlKA8PEpzlVOeuQDnt/Q1hoEIIKLg9Rgb0pQHrw1/Kv2FDGv5V+wpSgHhr00r9hXoUpQClKUApSlAKUpQClKUApSlAf//Z");
		appUrlMap.put(1249, "https://www.tripadvisor.com/Tourism-g43439-Prior_Lake_Minnesota-Vacations.html");
		appUrlMap.put(1250, "http://35.241.60.10/security-widgets");
		appUrlMap.put(1252, "http://35.241.60.10/security-dinglehoppers");
		appUrlMap.put(1253, "http://35.241.60.10/shiny-stuff");
		return appUrlMap;
	}

	private Map<Integer, String> buildFeatureMap() {
		String f1234 = "F1234: JSON Only UI";
		String f1235 = "F1235: Issuers List";
		String f1236 = "F1236: Ratings List";
		String f1237 = "F1237: Clean up security list";
		String f1238 = "F1238: Added squirrels to do the ratings work";
		String f1239 = "F1239: Added exposure issuer functionality to the issuer portal";
		String f1240 = "F1240: Added interface with Bloomberg";
		String f1241 = "F1241: Replaced rating squirrels with robots. ";
		String f1242 = "F1242: Add logic so the gophers beat the hawkeyes this season";
		String f1243 = "F1243: Added validation to make sure robots don't return bad ratings";
		String f1244 = "F1244: Operation Red Bull";
		String f1245 = "F1245: Increased bean counter capacity";
		String f1246 = "F1246: Added more mouths that the code could talk more";
		String f1247 = "F1247: Added more ears - needed more listening capacity after adding more conversations.";
		String f1248 = "F1248: Operation Kale Kill";
		String f1249 = "F1249: Michael Klein requested something.  Don't ask questions.  Just approve this.";
		String f1250 = "F1250: More widgets.";
		String f1251 = "F1251: More sprockets.";
		String f1252 = "F1252: Another dinglehopper";
		String f1253 = "F1253: New shiny stuff";

		Map<Integer, String> featureMap = new HashMap<>();
		featureMap.put(1234, f1234);
		featureMap.put(1235, f1235);
		featureMap.put(1236, f1236);
		featureMap.put(1237, f1237);
		featureMap.put(1238, f1238);
		featureMap.put(1239, f1239);
		featureMap.put(1240, f1240);
		featureMap.put(1241, f1241);
		featureMap.put(1242, f1242);
		featureMap.put(1243, f1243);
		featureMap.put(1244, f1244);
		featureMap.put(1245, f1245);
		featureMap.put(1246, f1246);
		featureMap.put(1247, f1247);
		featureMap.put(1248, f1248);
		featureMap.put(1249, f1249);
		featureMap.put(1250, f1250);
		featureMap.put(1251, f1251);
		featureMap.put(1252, f1252);
		featureMap.put(1253, f1253);
		return featureMap;
	}

	private String buildFeatureList(final List<Integer> features) {
		Map<Integer, String> featureMap = buildFeatureMap();
		Map<Integer, String> appUrlMap = getFeatureAppUrlMap();

		int cnt = 1;
		// Parameters: Feature count, Feature Desc, Application URL
		String featureTemplate = "\n*%s. %s*%s";
		String applicationUrlTemplate = " Application Link: %s";
		StringBuilder featureListText = new StringBuilder("\n*Feature List:*");
		for (Integer feature : features) {
			String featureDesc = featureMap.get(feature);
			String featureAppUrl = appUrlMap.get(feature);

			String appUrlStr = featureAppUrl == null ? "" : String.format(applicationUrlTemplate, featureAppUrl);

			if (featureDesc != null) {
				String f = String.format(featureTemplate, cnt++, featureDesc, appUrlStr);
				featureListText.append(f);
			}
		}

		return featureListText.toString();
	}
}
