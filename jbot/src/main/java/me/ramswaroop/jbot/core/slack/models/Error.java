package me.ramswaroop.jbot.core.slack.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Created by ramswaroop on 19/06/2016.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Error {
    private int code;
    private String msg;

    public Error() {
		super();
	}

	public Error(String msg) {
		super();
		this.msg = msg;
	}

	public Error(int code, String msg) {
		super();
		this.code = code;
		this.msg = msg;
	}

	public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }
}
