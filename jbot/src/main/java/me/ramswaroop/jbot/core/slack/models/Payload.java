package me.ramswaroop.jbot.core.slack.models;

import com.fasterxml.jackson.annotation.JsonProperty;

import me.ramswaroop.jbot.core.facebook.models.User;

public class Payload {

	private String type;
	private Action[] actions;
	@JsonProperty("callback_id")
	private String callbackId;
	private Team team;
	private Channel channel;
	private User user;
	@JsonProperty("action_ts")
	private String actionTs;
	@JsonProperty("message_ts")
	private String messageTs;
	@JsonProperty("attachment_id")
	private String attachmentId;
	private String token;
	@JsonProperty("is_app_unfurl")
	private boolean isAppUnfurl;
	@JsonProperty("response_url")
	private String responseUrl;
	@JsonProperty("trigger_id")
	private String triggerId;
	@JsonProperty("original_message")
	private RichMessage originalMessage;
	
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
	public Action[] getActions() {
		return actions;
	}
	public void setActions(Action[] actions) {
		this.actions = actions;
	}
	public String getCallbackId() {
		return callbackId;
	}
	public void setCallbackId(String callbackId) {
		this.callbackId = callbackId;
	}
	public Team getTeam() {
		return team;
	}
	public void setTeam(Team team) {
		this.team = team;
	}
	public Channel getChannel() {
		return channel;
	}
	public void setChannel(Channel channel) {
		this.channel = channel;
	}
	public User getUser() {
		return user;
	}
	public void setUser(User user) {
		this.user = user;
	}
	public String getActionTs() {
		return actionTs;
	}
	public void setActionTs(String actionTs) {
		this.actionTs = actionTs;
	}
	public String getMessageTs() {
		return messageTs;
	}
	public void setMessageTs(String messageTs) {
		this.messageTs = messageTs;
	}
	public String getAttachmentId() {
		return attachmentId;
	}
	public void setAttachmentId(String attachmentId) {
		this.attachmentId = attachmentId;
	}
	public String getToken() {
		return token;
	}
	public void setToken(String token) {
		this.token = token;
	}
	public boolean isAppUnfurl() {
		return isAppUnfurl;
	}
	public void setAppUnfurl(boolean isAppUnfurl) {
		this.isAppUnfurl = isAppUnfurl;
	}
	public String getResponseUrl() {
		return responseUrl;
	}
	public void setResponseUrl(String responseUrl) {
		this.responseUrl = responseUrl;
	}
	public String getTriggerId() {
		return triggerId;
	}
	public void setTriggerId(String triggerId) {
		this.triggerId = triggerId;
	}
	public RichMessage getOriginalMessage() {
		return originalMessage;
	}
	public void setOriginalMessage(RichMessage originalMessage) {
		this.originalMessage = originalMessage;
	}


 }
