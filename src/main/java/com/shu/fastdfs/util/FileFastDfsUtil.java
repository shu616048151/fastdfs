package com.shu.fastdfs.util;

import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.PooledObjectFactory;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.apache.log4j.Logger;
import org.csource.common.NameValuePair;
import org.csource.fastdfs.*;
import org.springframework.web.multipart.MultipartFile;

import javax.activation.MimetypesFileTypeMap;
import java.io.*;
import java.util.Properties;
import java.util.ResourceBundle;


public class FileFastDfsUtil{
    private static Logger logger = Logger.getLogger(FileFastDfsUtil.class);

    private static String server;
    private static Boolean debug;
    private static String host;
    private static String debug_host = "http://39.108.180.232/";
    public static String getServer() {
        return server;
    }

    public static Boolean getDebug() {
        return debug;
    }

    public static String getHost() {
        return host;
    }

    public static String getDebug_host() {
        return debug_host;
    }

    private static TrackerClient trackerClient;
    private static GenericObjectPool<StorageClient> clientPool;
    private static int reconnect_times;

    private volatile static FileFastDfsUtil fileFastDfsUtil;
    private FileFastDfsUtil() {

    }
    public static FileFastDfsUtil getInstance() {
        if (fileFastDfsUtil == null) {
            synchronized (FileFastDfsUtil.class) {
                if (fileFastDfsUtil == null) {
                    fileFastDfsUtil = new FileFastDfsUtil();
                }
            }
        }
        return fileFastDfsUtil;
    }

    static {
        try {
            /**读配置文件*/
            ResourceBundle bundle = ResourceBundle.getBundle("dfs_server");
            server = bundle.getString("server");
            debug = bundle.getString("debug").equals("true");
            host = bundle.getString("host");
            if(debug){//本地运行测试
                host = debug_host;
            }
            /**初始化fdfs客户端连接池相关*/
            //优先采用properties方式读取配置文件
            Properties properties=new Properties();
            InputStream is = FileFastDfsUtil.class.getClassLoader().getResourceAsStream("fastdfs-client.properties");
            properties.load(is);

            ClientGlobal.initByProperties(properties);
            trackerClient = new TrackerClient();
            reconnect_times =3;
            GenericObjectPoolConfig config = new GenericObjectPoolConfig();
            config.setMaxTotal(8);
            config.setMaxWaitMillis(10000);
            clientPool = new GenericObjectPool<StorageClient>(new ClientFactory(), config);


        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
    }

    private static class ClientFactory implements PooledObjectFactory<StorageClient> {

        public PooledObject<StorageClient> makeObject() throws Exception {
            TrackerServer trackerServer = trackerClient.getTrackerServer();
            StorageServer storageServer = null;
            StorageClient storageClient = new StorageClient(trackerServer, storageServer);
            return new DefaultPooledObject<StorageClient>(storageClient);
        }

        public void destroyObject(PooledObject<StorageClient> pooledObject) throws Exception {
        }

        public boolean validateObject(PooledObject<StorageClient> pooledObject) {
            return true;
        }

        public void activateObject(PooledObject<StorageClient> pooledObject) throws Exception {
        }

        public void passivateObject(PooledObject<StorageClient> pooledObject) throws Exception {
        }
    }

    /**上传MultipartFile文件*/
    public String upload(String userId, MultipartFile file) throws Exception {
        NameValuePair[] meta_list = new NameValuePair[3];
        meta_list[0] = new NameValuePair("contentType", file.getContentType());
        meta_list[1] = new NameValuePair("fileName", file.getOriginalFilename());
        meta_list[2] = new NameValuePair("size", Long.toString(file.getSize()));
        String[] uploadResults = null;
        int dot = file.getOriginalFilename().lastIndexOf(".");//返回指定字符串最后出现的位置
        String ext = dot == -1 ? null : file.getOriginalFilename().substring(dot + 1);
        for (int i = 0; i < reconnect_times; i++) {
            StorageClient storageClient = clientPool.borrowObject();
            try {
                uploadResults = storageClient.upload_file(file.getBytes(), ext, meta_list);
                clientPool.returnObject(storageClient);
                break;
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                logger.warn("upload file fail, reconnecting...");
                clientPool.returnObject(storageClient);
                clientPool.invalidateObject(storageClient);
            }
            if (uploadResults == null) {
                continue;
            }
        }
        if (uploadResults == null) {
            logger.error("upload file fail!!!");
            throw new Exception("上传FastDFS服务器失败");
        }
        String groupName = uploadResults[0];
        String remoteFileName = uploadResults[1];
        logger.info("upload file successfully!!!\tfilename:" + file.getOriginalFilename() + "\tgroup_name:" + groupName
                + "\tremoteFileName:" + remoteFileName);
        return host+uploadResults[0] + "/" + uploadResults[1];//返回存储路径以便写入数据库
    }

    /**上传File文件*/
    public String upload(String userId, File file) throws Exception {
        NameValuePair[] meta_list = new NameValuePair[3];
        meta_list[0] = new NameValuePair("contentType", new MimetypesFileTypeMap().getContentType(file));
        meta_list[1] = new NameValuePair("fileName", file.getName());
        meta_list[2] = new NameValuePair("size", Long.toString(file.length()));
        String[] uploadResults = null;
        int dot = file.getName().lastIndexOf(".");//返回指定字符串最后出现的位置
        String ext = dot == -1 ? null : file.getName().substring(dot + 1);
        for (int i = 0; i < reconnect_times; i++) {
            StorageClient storageClient = clientPool.borrowObject();
            try {
                //File -> byte[]
                byte[] buffer;
                FileInputStream fis = new FileInputStream(file);
                ByteArrayOutputStream bos = new ByteArrayOutputStream(1000);
                byte[] b = new byte[1000];
                int n;
                while ((n = fis.read(b)) != -1) {
                    bos.write(b, 0, n);
                }
                fis.close();
                bos.close();
                buffer = bos.toByteArray();

                uploadResults = storageClient.upload_file(buffer, ext, meta_list);
                clientPool.returnObject(storageClient);
                break;
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                logger.warn("upload file fail, reconnecting...");
                clientPool.returnObject(storageClient);
                clientPool.invalidateObject(storageClient);
            }
            if (uploadResults == null) {
                continue;
            }
        }
        if (uploadResults == null) {
            logger.error("upload file fail!!!");
            throw new Exception("上传FastDFS服务器失败");
        }
        String groupName = uploadResults[0];
        String remoteFileName = uploadResults[1];
        logger.info("upload file successfully!!!\tfilename:" + file.getName() + "\tgroup_name:" + groupName
                + "\tremoteFileName:" + remoteFileName);
        return host+uploadResults[0] + "/" + uploadResults[1];//返回存储路径以便写入数据库
    }

    /**上传文件流*/
    public String upload(String userId, MultipartFile file, ByteArrayOutputStream out) throws Exception {
        NameValuePair[] meta_list = new NameValuePair[3];
        meta_list[0] = new NameValuePair("contentType", file.getContentType());
        meta_list[1] = new NameValuePair("fileName", file.getOriginalFilename());
        meta_list[2] = new NameValuePair("size", Integer.toString(out.size()));
        String[] uploadResults = null;
        int dot = file.getOriginalFilename().lastIndexOf(".");
        String ext = dot == -1 ? null : file.getOriginalFilename().substring(dot + 1);
        for (int i = 0; i < reconnect_times; i++) {
            StorageClient storageClient = clientPool.borrowObject();
            try {
                uploadResults = storageClient.upload_file(out.toByteArray(), ext, meta_list);
                clientPool.returnObject(storageClient);
                break;
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                logger.warn("upload file fail, reconnecting...");
                clientPool.returnObject(storageClient);
                clientPool.invalidateObject(storageClient);
            }
            if (uploadResults == null) {
                continue;
            }
        }
        if (uploadResults == null) {
            logger.error("upload file fail!!!");
            throw new Exception("上传FastDFS服务器失败");
        }
        String groupName = uploadResults[0];
        String remoteFileName = uploadResults[1];
        logger.info("upload file successfully!!!\tfilename:" + file.getOriginalFilename() + "\tgroup_name:" + groupName
                + "\tremoteFileName:" + remoteFileName);
        return host+uploadResults[0] + "/" + uploadResults[1];//返回存储路径以便写入数据库
    }

    public void delete(String path) throws Exception {
        int deleteResults = -1;
        path = path.replaceAll("^(http[s]*://[^/]*)*/*", "");
        int split = path.indexOf('/');
        String group = path.substring(0, split);
        path = path.substring(split + 1);
        for (int i = 0; i < reconnect_times; i++) {
            StorageClient storageClient = clientPool.borrowObject();
            try {
                deleteResults = storageClient.delete_file(group, path);
                clientPool.returnObject(storageClient);
                break;
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                logger.warn("delete file fail, reconnecting...");
                clientPool.returnObject(storageClient);
                clientPool.invalidateObject(storageClient);
            }
            if (deleteResults != 0) {
                continue;
            }
        }
        if (deleteResults != 0) {
            logger.error("delete file fail!!!");
            throw new Exception("删除FastDFS服务器文件失败");
        }
        logger.info("delete file successfully!!!\tfilename:" + path);
    }

}
