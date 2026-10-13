package com.ultimate.inventory.utils;


import com.ultimate.inventory.DTO.ResponseDTO;
import com.ultimate.inventory.Repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Service
public class FileStorage {

    @Value("${default.upload}")
    private String defaultFileUpload;

    @Autowired
    private UserRepository userRepo;

    //save a file
    public ResponseDTO saveFile(MultipartFile file, String email) throws IOException {
        ResponseDTO response = new ResponseDTO();
        //create folder
        if (file.isEmpty() || email == null || email.isEmpty()) {
            response.setStatus(false);
            response.setMessage("Empty parameters");
            return response;
        }

        //save a file in folder
        String folderName = email.replaceAll("[^a-zA-Z0-9]", "_");
        File folder = new File(defaultFileUpload, folderName);

        if (!folder.exists()) {
            boolean isCreated = folder.mkdirs();
            System.out.println("FOLDER CREATED :" + isCreated);
        }

        String fileName =file.getOriginalFilename() != null ? file.getOriginalFilename():UUID.randomUUID() + "_" + file.getOriginalFilename() ;
        File saveFile = new File(folder, fileName);

        int indexDot = fileName.lastIndexOf(".");
        String actualFileName = fileName.substring(0, indexDot);
        String ext = fileName.substring(indexDot);
        int count = 1;

        while(saveFile.exists()){
            fileName = actualFileName + "(" + count + ")."+ext;
            saveFile = new File(folder, fileName);
            count++;
        }

        file.transferTo(saveFile);

        System.out.println("getOriginalFilename" + file.getOriginalFilename());
        System.out.println("getName" + file.getName());
        System.out.println("getContentType" + file.getContentType());
        response.setStatus(true);
        response.setMessage(fileName);
        return response;
    }

    //delete file
    public boolean deleteFile(String fileName, String email) {

        if (email == null || fileName == null || email.isEmpty() || fileName.isEmpty()) {
            System.out.println("THE FOLLOWING WAS EMPTY");
            System.out.println("Email " + email);
            System.out.println("fileName " + fileName);
            return false;
        }

        //find the file and delete it
        String folderName = email.replaceAll("[^a-zA-Z0-9]", "_");
        File folder = new File(defaultFileUpload, folderName);
        File saveFile = new File(folder, fileName);

        System.out.println("FILEnAME" + fileName);
        System.out.println("Folder " + folder.getAbsoluteFile());
        System.out.println("saveFile Path " + saveFile.getAbsoluteFile());
        System.out.println("saveFile " + saveFile.getParentFile());


        boolean isDeleted = saveFile.delete();
        System.out.println("FILE WAS DELETED :" + isDeleted);


        return isDeleted;
    }

    //delete folder
    public boolean deleteFolder(String fileName, String email) {

        if (email == null || fileName == null || email.isEmpty() || fileName.isEmpty()) {
            return false;
        }

        //find the file and delete it
        String folderName = email.replaceAll("[^a-zA-Z0-9]", "_");
        File folder = new File(defaultFileUpload, folderName);


        folder.deleteOnExit();

        return true;

    }

}
