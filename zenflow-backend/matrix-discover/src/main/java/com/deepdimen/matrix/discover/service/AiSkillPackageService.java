package com.deepdimen.matrix.discover.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.deepdimen.matrix.discover.vo.AiSkillAdminVO;
import com.deepdimen.matrix.discover.vo.AiSkillImportVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface AiSkillPackageService {

	Page<AiSkillAdminVO> page(Page<AiSkillAdminVO> page, String keyword, String packageStatus);

	void reorderSkills(List<Long> skillIds);

	AiSkillImportVO importPackage(MultipartFile file);

	AiSkillImportVO updatePackage(Long skillId, MultipartFile file);

	void approve(Long skillId);

	void disable(Long skillId);

	void enable(Long skillId);

	void publish(Long skillId);

	void unpublish(Long skillId);

	void delete(Long skillId);

	SkillPackageDownload download(Long packageId);

	record SkillPackageDownload(byte[] content, String filename, String sha256, long size) {
	}

}
