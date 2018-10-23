package org.xprc.common.transport.body;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xprc.common.exception.remoting.RemotingCommmonCustomException;
import org.xprc.common.utils.Status;

public class ResponseCustomBody implements CommonCustomBody {

	private static final Logger logger = LoggerFactory.getLogger(ResponseCustomBody.class);

	private byte status = Status.OK.value();

	private ResultWrapper resultWrapper;

	public ResponseCustomBody(byte status, ResultWrapper resultWrapper) {
		this.status = status;
		this.resultWrapper = resultWrapper;
	}

	public byte getStatus() {
		return status;
	}

	public void setStatus(byte status) {
		this.status = status;
	}

	public ResultWrapper getResultWrapper() {
		return resultWrapper;
	}

	public void setResultWrapper(ResultWrapper resultWrapper) {
		this.resultWrapper = resultWrapper;
	}

	@Override
	public void checkFields() throws RemotingCommmonCustomException {
	}

	public static class ResultWrapper {

		private Object result;
		private String error;

		public Object getResult() {
			return result;
		}

		public void setResult(Object result) {
			this.result = result;
		}

		public String getError() {
			return error;
		}

		public void setError(String error) {
			this.error = error;
		}
	}

	public Object getResult() {

		if (status == Status.OK.value()) {
			return getResultWrapper().getResult();
		} else {
			logger.warn("get result occor exception [{}]", getResultWrapper().getError());
			return null;
		}
	}

}
