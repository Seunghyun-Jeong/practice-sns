package com.example.sns.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 업로드된 이미지 파일을 저장하고 접근 URL을 돌려주는 공용 서비스.
 * 프로필 이미지, 게시글 이미지 등에서 재사용한다.
 */
@Service
public class FileStorageService {

    @Value("${file.upload-dir}")
    private String uploadDir;

    /**
     * 이미지 파일을 저장하고 접근 URL(/uploads/파일명)을 반환한다.
     * 파일이 없거나 비어 있으면 null을 반환한다.
     *
     * @param prefix 저장 파일명 접두어 (예: "profile_5", "post_5")
     */
    public String storeImage(MultipartFile file, String prefix) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("이미지 파일만 업로드할 수 있습니다.");
        }

        String original = file.getOriginalFilename();
        String ext = (original != null && original.contains("."))
                ? original.substring(original.lastIndexOf('.'))
                : "";
        String filename = prefix + "_" + System.currentTimeMillis() + ext;

        try {
            Path dir = Paths.get(uploadDir).toAbsolutePath();
            Files.createDirectories(dir);
            Files.copy(file.getInputStream(), dir.resolve(filename));
        } catch (IOException e) {
            throw new RuntimeException("이미지 저장에 실패했습니다.", e);
        }

        return "/uploads/" + filename;
    }
}
