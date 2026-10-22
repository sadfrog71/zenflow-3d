package com.deepdimen.matrix.work.job;

import com.deepdimen.matrix.work.service.InformationCollectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component("workInformationDailyJob")
@RequiredArgsConstructor
public class WorkInformationDailyJob {

	private final InformationCollectionService collectionService;

	public String run(String policyId) {
		return collectionService.runPolicy(Long.valueOf(policyId)).getStatus();
	}
}
