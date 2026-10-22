package com.deepdimen.matrix.admin.mobilepush;

public interface PushProvider {

	String channel();

	Result send(String token, String messageId, String bindingId, String notificationTag);

	record Result(String status, String receipt, String failureCode) {
		public static Result retry(String code) {
			return new Result("RETRY", null, code);
		}
	}

}
