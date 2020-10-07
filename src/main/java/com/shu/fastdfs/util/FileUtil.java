package com.shu.fastdfs.util;

import org.apache.log4j.Logger;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.File;

public class FileUtil {
    private static FileFastDfsUtil fileFastDfsUtil;
    private static Logger logger = Logger.getLogger(FileUtil.class);
    static{
        fileFastDfsUtil= FileFastDfsUtil.getInstance();
    }

    public static String upload(String userId, MultipartFile file) throws Exception{
        return fileFastDfsUtil.upload(userId, file);
    }

    public static String upload(String userId, File file) throws Exception{
        return fileFastDfsUtil.upload(userId, file);
    }

    public static String upload(String userId, MultipartFile file, ByteArrayOutputStream out) throws Exception{
        return fileFastDfsUtil.upload(userId, file, out);
    }

    public static void delete(String path) throws Exception{
        fileFastDfsUtil.delete(path);
    }

}
