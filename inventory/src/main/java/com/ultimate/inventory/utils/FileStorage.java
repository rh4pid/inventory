package com.ultimate.inventory.utils;

import com.ultimate.inventory.DTO.ResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Service
public class FileStorage {

    private static final Logger log = LoggerFactory.getLogger(FileStorage.class);

    @Value("${default.upload}")
    private String defaultFileUpload;

    public ResponseDTO saveFile(MultipartFile file, String email) throws IOException {
        ResponseDTO response = new ResponseDTO();

        if (file == null || file.isEmpty() || email == null || email.isEmpty()) {
            response.setStatus(false);
            response.setMessage("Empty parameters");
            return response;
        }

        String folderName = email.replaceAll("[^a-zA-Z0-9]", "_");
        File folder = new File(defaultFileUpload, folderName);

        if (!folder.exists()) {
            boolean created = folder.mkdirs();
            log.debug("Created upload folder {}: {}", folder.getAbsolutePath(), created);
        }

        String originalName = file.getOriginalFilename() != null
                ? file.getOriginalFilename()
                : UUID.randomUUID() + "_file";

        int dotIndex = originalName.lastIndexOf(".");
        String baseName = dotIndex >= 0 ? originalName.substring(0, dotIndex) : originalName;
        String ext = dotIndex >= 0 ? originalName.substring(dotIndex) : "";

        String fileName = originalName;
        File target = new File(folder, fileName);
        int count = 1;
        while (target.exists()) {
            fileName = baseName + "(" + count++ + ")" + ext;
            target = new File(folder, fileName);
        }

        file.transferTo(target);
        log.info("File saved: {}", target.getAbsolutePath());

        response.setStatus(true);
        response.setMessage(fileName);
        return response;
    }

    public boolean deleteFile(String fileName, String email) {
        if (email == null || fileName == null || email.isEmpty() || fileName.isEmpty()) {
            log.warn("deleteFile called with empty parameters: email={}, fileName={}", email, fileName);
            return false;
        }

        String folderName = email.replaceAll("[^a-zA-Z0-9]", "_");
        File target = new File(new File(defaultFileUpload, folderName), fileName);
        boolean deleted = target.delete();
        log.info("File delete {}: {}", target.getAbsolutePath(), deleted);
        return deleted;
    }

    public boolean deleteFolder(String email) {
        if (email == null || email.isEmpty()) return false;
        String folderName = email.replaceAll("[^a-zA-Z0-9]", "_");
        File folder = new File(defaultFileUpload, folderName);
        folder.deleteOnExit();
        return true;
    }
}
