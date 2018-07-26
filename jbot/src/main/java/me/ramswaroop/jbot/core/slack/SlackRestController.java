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

import org.apache.commons.lang3.StringUtils;
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
	private static final String APPROVE_TXT = "Click 'Approve' after successful application checkout.";
	
	@RequestMapping(consumes = {
			MediaType.APPLICATION_FORM_URLENCODED_VALUE }, method = RequestMethod.POST, value = "/receive/message")
	public ResponseEntity<RichMessage> receiveInteractiveMessage(WebRequest request) {
		String payloadJson = request.getParameter("payload");
		Payload payload = JsonUtil.deSerialize(payloadJson, Payload.class);
		Attachment[] originalAttachments = payload.getOriginalMessage().getAttachments();
		Attachment originalAttachment = originalAttachments[0];
		String originalText = originalAttachment.getText();
		originalText = StringUtils.remove(originalText, APPROVE_TXT);
		originalText = StringUtils.remove(originalText, "<");
		originalText = StringUtils.remove(originalText, ">");
		logger.info(payloadJson);
		String response_text;
		if (payload.getActions()[0].getName().equals("approve")) {
			response_text = approve(payload.getUser().getName(), payload.getActions()[0].getValue(), originalText);
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

	private String approve(String user, String value, String originalText) { // post to GoCD
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
				+ value + "\n" + originalText;
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
		StringBuilder sb = new StringBuilder(APPROVE_TXT);

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
