package com.shu.fastdfs.test;

import com.shu.fastdfs.util.FileUtil;

import java.io.File;

/**
 * @Author shuxibing
 * @Date 2020/10/7 13:49
 * @Uint d9lab_2019
 * @Description:  文件上传测试
 */
public class FileTest {

  public static void main(String[] args) throws Exception {
      File file=new File("C:\\Users\\舒细兵\\Desktop\\hehe_release_1.0_v3.apk");
      String url = FileUtil.upload("1", file);
      System.out.println(url);
  }

}
