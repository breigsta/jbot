package me.ramswaroop.jbot.core.slack;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.ProcessBuilder.Redirect;
import java.util.ArrayList;
import java.util.List;

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

	@RequestMapping(consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE }, method = RequestMethod.POST, value = "/receive/message")
//	public ResponseEntity<RichMessage> receiveInteractiveMessage(-EITHER- @RequestParam("payload") Payload payload -OR- WebRequest request) {
	public ResponseEntity<RichMessage> receiveInteractiveMessage(@RequestParam("payload") Payload payload) {
		
		String response_text;
		if (payload.getActions()[0].getName().equals("approve")) {
			response_text = approve(payload.getUser().getContactName(), payload.getActions()[0].getValue());
		}
				else if (payload.getActions()[0].getName().equals("reject")) {
					response_text = reject(payload.getUser().getContactName(), payload.getActions()[0].getValue());
				} else {
					response_text = "I dont know what to do with this.";
				}
		RichMessage message = new RichMessage();
		message.setText(response_text);
		message.setType("ephemeral");

		return new ResponseEntity<RichMessage>(message, HttpStatus.OK);
	}
    
	private String approve(String user, String value) {		// post to GoCD
		String url = "https://" + user + ":" + slackConfig.getP() + "@localhost:8154/go/run/" + value + "/BusinessApproval";
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
			logger.info("GoCD Exit code: " + process.exitValue() + "; Response Text: \r\n" + result);
		} catch (IOException | InterruptedException e) {
			logger.error("Error calling GoCD", e);
		}
		// Send same responseText in response and to #Deployments channel 
		String responseText = ":heavy_check_mark: " + user + " approved pipeline " + value + " for deployment to production.\nPipeline Link: https://localhost:8154/go/pipelines/value_stream_map/PortfolioManagementApplication/"+value;
		return responseText;
}



	@RequestMapping(consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE }, method = RequestMethod.POST, value = "/receive/slash-command")
	public ResponseEntity<RichMessage> receiveSlashCommand(@RequestParam("token") String token,
			@RequestParam("team_id") String teamId, @RequestParam("team_domain") String teamDomain,
			@RequestParam("channel_id") String channelId, @RequestParam("channel_name") String channelName,
			@RequestParam("user_id") String userId, @RequestParam("user_name") String userName,
			@RequestParam("command") String command, @RequestParam("text") String text,
			@RequestParam("response_url") String responseUrl) {
		
		if (token != slackConfig.getVerificationToken()) {
			throw new RuntimeException(String.format("Did not receive the expected token.  Expected [%s]  Actual [%s]", slackConfig.getVerificationToken(), token));
		}
		
		String response_text = null;
		String response_type = null;
		
		if (command == "/echo") {
				if (text == "help") { 
					response_text = "I echo back what you tell me.\nTry typing `/echo hello` to see.";
					response_type = "ephemeral";
				} else {
					response_text = "token = " + token + ", team_id = " + teamId + ", team_domain = " + teamDomain + ", channel_id = " + channelId + ", channel_name =" + channelName + 	"user_id" + userId + "user_name" + userName + 
							", command = " + command + ", text = " + text + ", response_url =" + responseUrl;
					response_type = "in_channel";
				}
		} else if (command == "/approve") {
				if (text == "help") {
					response_text = "I provide your business approval for a production release. Try typing `/approve -1` to see.";
					response_type = "ephemeral";
				} else {
//					* approve pipeline
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
		String url = "https://" + user + ":" + slackConfig.getP() + "@localhost:8154/go/run/PortfolioManagementApplication/"+value+"/BusinessApproval";
		ProcessBuilder pb =  new ProcessBuilder("curl", "-XPOST", url, "-k", "-H", "Confirm: true");
		 File log = new File("d:\\log.txt");
		 pb.redirectErrorStream(true);
		 pb.redirectOutput(Redirect.appendTo(log));
		try {
			pb.start();
		} catch (IOException e) {
			logger.error("Error calling GoCD",e);
		}
		// Send same responseText in response and to #Deployments channel 
		String responseText = ":heavy_check_mark: " + user + " approved pipeline " + value + " for deployment to production.\nPipeline Link: https://localhost:8154/go/pipelines/value_stream_map/PortfolioManagementApplication/"+value;
		return responseText;
    }
    
    private String reject(String user, String value) {
    	// Send same responseText in response and to #Deployments channel 
    			String responseText = ":x: " + user + " rejected pipeline " + value + " for deployment to production.\nPipeline Link: https://localhost:8154/go/pipelines/value_stream_map/PortfolioManagementApplication/"+value;
    			return responseText;
    }
    
	@RequestMapping(method = RequestMethod.GET, value = "/webhook")
	public ResponseEntity<String> sendWebhookMessage(@RequestParam(value = "pipeline") String pipeline,@RequestParam(value = "instance") 
		String instance,@RequestParam(value = "stage") String stage) {
		RichMessage msg = null;
		if (stage.equals("ProjectTeamRequest4Approval")) { 
	//		send approvalMessage to #business-approvals
			msg = getApprovalMessage(instance);
			sendBusinessApprovalMessage(msg);
		}
		else if (stage.equals("BusinessApproval")) { 
	//		send deploymentNotificationMessage to #deployments
			msg = getDeploymentNotificationMessage(pipeline, instance);
			sendDeploymentMessage(msg);
		}else if (stage.equals("push")) {
	//		send deployedNotificationMessage to #deployments
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

	private RichMessage getDeployedNotificationMessage(String pipeline, String instance) {
		return new RichMessage("##deployments", "PortfolioManagementApplication Deployment",
				":racing_car: A production deployment is in progress! Click here for details:\n https://localhost:8154/go/pipelines/value_stream_map/"
						+ pipeline + "/" + instance,
				":racing_car:", "in_channel", null);
	}

	private RichMessage getDeploymentNotificationMessage(String pipeline, String instance) {
		return new RichMessage("##deployments", "PortfolioManagementApplication Deployment",
				":checkered_flag: A production deployment complete successfully! Click here for details:\n https://localhost:8154/go/pipelines/value_stream_map/"
						+ pipeline + "/" + instance,
				":checkered_flag:", "in_channel", null);
		}

	private RichMessage getApprovalMessage(String instance) {
		Action[] actions = new Action[] {
			new Action("1", "approve", "Approve", "button", instance, "primary", new Confirm("Please confirm your decision", "Are you sure you want to approve this build?", "Yes", "No")),
			new Action("2", "reject", "Reject", "button", instance, "danger", new Confirm("Please confirm your decision", "Are you sure you want to reject this build?", "Yes", "No"))
		};
		Attachment[] attachments = new Attachment[] {
		new Attachment(
				"Click 'Approve' after successful application checkout.\n*Feature List:*\n*1. F1234: JSON only UI*\n*2. F1235: Issuers List*  Application Link: http://35.241.60.10/portfolio/issuers\n*3. F1236: Ratings List*  Application Link: http://35.241.60.10/security-rating/ratings",
				"You are unable to approve a deployment", "approve", "#3AA3E3", "default", null, null, null, null, null,
				null, null, null, null, null, null, null, null, actions)};
		return new RichMessage("##business-approvals", "PortfolioManagementApplication Deployment",
				":question: Please approve a deployment to production.", ":question:", "in_channel", attachments);

	}
}
