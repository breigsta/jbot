package me.ramswaroop.jbot.core.slack.models;

public class Action {
	private String id;
	private String name;
	private String text;
	private String type;
	private String value;
	private String style;
	private Confirm confirm;
	
	public Action(String id, String name, String text, String type, String value, String style, Confirm confirm) {
		super();
		this.id = id;
		this.name = name;
		this.text = text;
		this.type = type;
		this.value = value;
		this.style = style;
		this.confirm = confirm;
	}
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getText() {
		return text;
	}
	public void setText(String text) {
		this.text = text;
	}
	public String getType() {
		return type;
	}
	public void setType(String type) {
		this.type = type;
	}
	public String getValue() {
		return value;
	}
	public void setValue(String value) {
		this.value = value;
	}
	public String getStyle() {
		return style;
	}
	public void setStyle(String style) {
		this.style = style;
	}
	public Confirm getConfirm() {
		return confirm;
	}
	public void setConfirm(Confirm confirm) {
		this.confirm = confirm;
	}
}
