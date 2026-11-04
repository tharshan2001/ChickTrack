package com.chick.app.common.service;

import com.chick.app.common.domain.DocumentSequence;
import com.chick.app.common.repository.DocumentSequenceRepository;
import com.chick.app.common.time.BusinessTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentNumberService {

	private final DocumentSequenceRepository sequences;

	public DocumentNumberService(DocumentSequenceRepository sequences) {
		this.sequences = sequences;
	}

	@Transactional
	public String next(String documentType, String prefix) {
		int year = BusinessTime.today().getYear();
		sequences.ensureExists(documentType, year);
		DocumentSequence sequence = sequences.findForUpdate(documentType, year);
		long value = sequence.getNextValue();
		sequence.setNextValue(value + 1);
		sequences.save(sequence);
		return prefix + "-" + year + "-" + String.format("%06d", value);
	}
}
