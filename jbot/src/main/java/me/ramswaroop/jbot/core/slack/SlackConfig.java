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
			public String getApi() {
				return api;
			}
			public void setApi(String api) {
				this.api = api;
			}
			public String getBotToken() {
				return botToken;
			}
			public void setBotToken(String botToken) {
				this.botToken = botToken;
			}
			public String getVerificationToken() {
				return verificationToken;
			}
			public void setVerificationToken(String verificationToken) {
				this.verificationToken = verificationToken;
			}
			public String getWebhookDeployment() {
				return webhookDeployment;
			}
			public void setWebhookDeployment(String webhookDeployment) {
				this.webhookDeployment = webhookDeployment;
			}
			public String getWebhookBusinessApproval() {
				return webhookBusinessApproval;
			}
			public void setWebhookBusinessApproval(String webhookBusinessApproval) {
				this.webhookBusinessApproval = webhookBusinessApproval;
			}
			public String getP() {
				return p;
			}
			public void setP(String p) {
				this.p = p;
			}
}
