package me.ramswaroop.jbot.core.slack;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "slack")
public class SlackConfig {
//	Set the following system env properties:
//		1. slack.api = https://slack.com/api
//		2. slack.botToken = https://api.slack.com/apps --> OAuth & Permissions --> Bot User OAuth Access Token
//		3. slack.verificationToken = https://api.slack.com/apps --> Basic Information --> Verification Token
//		4. slack.webhookDeployment = https://api.slack.com/apps --> Incoming Webhooks --> Webhook URL for #deployments
//		5. slack.webhookBusinessApproval = https://api.slack.com/apps -->  --> Incoming Webhooks --> Webhook URL for 	#business-approvals
//		6. slack.p = default GoCD pwd

		// use spring config to load the env props:
			private String api;
			private String botToken;
			private String verificationToken;
			private String webhookDeployment;
			private String webhookBusinessApproval;
			private String p;
}
