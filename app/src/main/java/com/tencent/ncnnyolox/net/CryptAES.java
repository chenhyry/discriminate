package com.tencent.ncnnyolox.net;


import android.util.Base64;

import java.security.Key;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * 网络加密解密
 *
 * @Author: xiongxiaojun
 * @Date: 2019/6/13 19:32
 */
public class CryptAES {

   private static final String AESTYPE = "AES/ECB/PKCS5Padding";

   /**
    * 加密
    *
    * @param plainText
    * @return
    */
   public static String encrypt(String plainText, String keyStr) {
      byte[] encrypt = null;
//        Log.v("json"," 明文密码***** " + plainText);
      try {
         Key key = generateKey(keyStr);
         Cipher cipher = Cipher.getInstance(AESTYPE);
         cipher.init(Cipher.ENCRYPT_MODE, key);
         encrypt = cipher.doFinal(plainText.getBytes());
      } catch (Exception e) {
         e.printStackTrace();
      }
      String ddd = new String(Base64.encode(encrypt, Base64.NO_WRAP));
      return ddd;
   }

   /**
    * 解密
    *
    * @param encryptData
    * @return
    */
   public static String decrypt(String encryptData, String keyStr) throws Exception {
      byte[] decrypt = null;
      try {
         Key key = generateKey(keyStr);
         Cipher cipher = Cipher.getInstance(AESTYPE);
         cipher.init(Cipher.DECRYPT_MODE, key);
         decrypt = cipher.doFinal(Base64.decode(encryptData, Base64.NO_WRAP));
      } catch (Exception e) {
         throw new Exception("illegal access!", e);
      }
      return new String(decrypt).trim();
   }

   private static Key generateKey(String key) throws Exception {
      try {
         SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(), "AES");
         return keySpec;
      } catch (Exception e) {
         e.printStackTrace();
         throw e;
      }
   }

   public static void main(String[] args) throws Exception {
      String str = decrypt("jLFwDbxTlPyqlm+GiwHYWmYRYxdTPUqPzssJ3XrhMy+JmnoIiGgzkU7PlZvc6hAZMoeHVaqEoMyCNJiLVzEp6hv5qneYt4OymHonLrQZLdQOMoiV0djWR3Fx/CETI1bOqneVbjOdS3myAq6ddmIdgU1E3h129NcQ38FEyklcKRH+iXEvGcAFazxRY5Ov3DlV5zn9N0KoF3sxdRARQVNYqaet15InAIwMbwXDQ9JZ0JFH7TaXrWcbO2wiaf0bcKbq5pB3pZF/E0eR2f1pGzXGXA==", "abcdefgabcdefg12");
      System.out.println(str);
   }
}
