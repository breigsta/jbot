package me.ramswaroop.jbot.core.slack.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author ramswaroop
 * @version 21/06/2016
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RichMessage {
	  private String channel;
	  private String username;
	  private String text;
	  @JsonProperty("icon_emoji")
	  private String iconEmoji;
	  @JsonProperty("response_type")
	  private String responseType;
		@JsonProperty("is_app_unfurl")
		private boolean isAppUnfurl;
		@JsonProperty("bot_id")
		private String botId;
		private String type;
		private String subType;
		private String ts;
	  private Attachment[] attachments;
	  
    public RichMessage() {
    }

    public RichMessage(String channel, String username, String text, String iconEmoji, String responseType,
			boolean isAppUnfurl, String botId, String type, String subType, String ts, Attachment[] attachments) {
		super();
		this.channel = channel;
		this.username = username;
		this.text = text;
		this.iconEmoji = iconEmoji;
		this.responseType = responseType;
		this.isAppUnfurl = isAppUnfurl;
		this.botId = botId;
		this.type = type;
		this.subType = subType;
		this.ts = ts;
		this.attachments = attachments;
	}
    public RichMessage(String channel, String username, String text, String iconEmoji, String responseType,
			 Attachment[] attachments) {
		super();
		this.channel = channel;
		this.username = username;
		this.text = text;
		this.iconEmoji = iconEmoji;
		this.responseType = responseType;		
		this.attachments = attachments;
	}

	public RichMessage(String text) {
        this.text = text;
    }
    
    public RichMessage encodedMessage() {
        this.setText(this.getText().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"));
        return this;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getIconEmoji() {
        return iconEmoji;
    }

    public void setIconEmoji(String iconEmoji) {
        this.iconEmoji = iconEmoji;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getResponseType() {
        return responseType;
    }

    public void setResponseType(String responseType) {
        this.responseType = responseType;
    }

    public Attachment[] getAttachments() {
        return attachments;
    }

    public void setAttachments(Attachment[] attachments) {
        this.attachments = attachments;
    }

	public boolean isAppUnfurl() {
		return isAppUnfurl;
	}

	public void setAppUnfurl(boolean isAppUnfurl) {
		this.isAppUnfurl = isAppUnfurl;
	}

	public String getBotId() {
		return botId;
	}

	public void setBotId(String botId) {
		this.botId = botId;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getSubType() {
		return subType;
	}

	public void setSubType(String subType) {
		this.subType = subType;
	}

	public String getTs() {
		return ts;
	}

	public void setTs(String ts) {
		this.ts = ts;
	}
}
