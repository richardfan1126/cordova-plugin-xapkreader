package com.flyingsoftgames.xapkreader;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;

import com.android.vending.expansion.zipfile.APKExpansionSupport;
import com.android.vending.expansion.zipfile.ZipResourceFile;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;

public class XAPKReader extends CordovaPlugin {
 @Override public void initialize (final CordovaInterface cordova, CordovaWebView webView) {
  String packageName = cordova.getActivity().getPackageName();
  final Bundle bundle = new Bundle ();
  
  // Get some data from the xapkreader.xml file.
  String[][] xmlData = new String[][] {
   {"xapk_main_version"           , "integer"},
   {"xapk_patch_version"          , "integer"},
   {"xapk_main_file_size"         , "integer", "long"},
   {"xapk_patch_file_size"        , "integer", "long"},
   {"xapk_expansion_authority"    , "string"},
   {"xapk_text_downloading_assets", "string"},
   {"xapk_text_preparing_assets"  , "string"},
   {"xapk_text_download_failed"   , "string"},
   {"xapk_text_error"             , "string"},
   {"xapk_text_close"             , "string"},
   {"xapk_google_play_public_key" , "string"}
  };
  int curlen = xmlData.length;
  for (int i = 0; i < curlen; i++) {
   int currentId = cordova.getActivity().getResources().getIdentifier (xmlData[i][0], xmlData[i][1], packageName);
   if (xmlData[i][1] == "bool")    {bundle.putBoolean(xmlData[i][0], cordova.getActivity().getResources().getBoolean(currentId)); continue;}
   if (xmlData[i][1] == "string")  {bundle.putString (xmlData[i][0], cordova.getActivity().getResources().getString (currentId)); continue;}
   if (xmlData[i][1] == "integer") {
    if ((xmlData[i].length == 2) || (xmlData[i][2] == "integer")) {
     bundle.putInt  (xmlData[i][0], cordova.getActivity().getResources().getInteger(currentId)); continue;
    }
    if (xmlData[i][2] == "long") {
     bundle.putLong (xmlData[i][0], cordova.getActivity().getResources().getInteger(currentId)); continue;
    }
   }
  }
  
  // Send data to the ContentProvider instance.
  ContentResolver cr = cordova.getActivity().getApplicationContext().getContentResolver();
  String expansionAuthority = bundle.getString("xapk_expansion_authority", "");
  cr.call (Uri.parse("content://" + expansionAuthority), "set_expansion_file_version_data", null, bundle);
  
  // Set the public key.
  XAPKDownloaderService.BASE64_PUBLIC_KEY = bundle.getString("xapk_google_play_public_key", "");
  
  cordova.getActivity().runOnUiThread (new Runnable() {
   @Override public void run () {
    XAPKDownloaderActivity.cordovaActivity = cordova.getActivity(); // Workaround for Cordova/Crosswalk flickering status bar bug.
    Context context = cordova.getActivity().getApplicationContext();
    Intent intent = new Intent(context, XAPKDownloaderActivity.class);
    intent.putExtras (bundle);
//    cordova.getActivity().startActivity (intent);
   }
  });
  
  super.initialize (cordova, webView);
 }


    private int mainVersion = 1;

    private int patchVersion = 1;

 /**
  * Executes the request.
  *
  * This method is called from the WebView thread. To do a non-trivial amount of work, use:
  *     cordova.getThreadPool().execute(runnable);
  *
  * To run on the UI thread, use:
  *     cordova.getActivity().runOnUiThread(runnable);
  *
  * @param action          The action to execute.
  * @param args            The exec() arguments.
  * @param callbackContext The callback context used when calling back into JavaScript.
  * @return                Whether the action was valid.
  * @throws JSONException
  *
  * @sa https://github.com/apache/cordova-android/blob/master/framework/src/org/apache/cordova/CordovaPlugin.java
  */
 @Override
 public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
  if (action.equals("get")) {
   final String filename = args.getString(0);
   final Context ctx = cordova.getActivity().getApplicationContext();
   cordova.getThreadPool().execute(new Runnable() {
    public void run() {
     try {
      // Read file
      PluginResult result = XAPKReader.readFile(ctx, filename, mainVersion, patchVersion, PluginResult.MESSAGE_TYPE_ARRAYBUFFER);
      callbackContext.sendPluginResult(result);
     }
     catch(Exception e) {
      e.printStackTrace();
      callbackContext.error(e.getLocalizedMessage());
     }
    }
   });
   return true;
  }
  return false;
 }

 /**
  * Read file in APK Expansion file.
  *
  * @param ctx      The context of the main Activity.
  * @param filename The filename to read
  * @return         PluginResult
  */
 private static PluginResult readFile(Context ctx, String filename, int mainVersion, int patchVersion, final int resultType) throws IOException {
  // Get APKExpensionFile
  ZipResourceFile expansionFile = APKExpansionSupport.getAPKExpansionZipFile(ctx, mainVersion, patchVersion);

  if (null == expansionFile) {
   throw new IOException("APKExpansionFile not found.");
  }

  // Find file in ExpansionFile
  AssetFileDescriptor fileDescriptor = expansionFile.getAssetFileDescriptor(filename);

  if (null == fileDescriptor) {
   throw new IOException("File not found (" + filename + ").");
  }

  // Read file
  InputStream inputStream = fileDescriptor.createInputStream();
  ByteArrayOutputStream os = new ByteArrayOutputStream();
  byte[] buffer = new byte[1024];
  int read = 0;
  while ((read = inputStream.read(buffer, 0, buffer.length)) != -1) {
   os.write(buffer, 0, read);
  }
  os.flush();

  // get file content type
  String contentType = URLConnection.guessContentTypeFromStream(inputStream);

  PluginResult result;
  switch (resultType) {
   case PluginResult.MESSAGE_TYPE_STRING:
    result = new PluginResult(PluginResult.Status.OK, os.toString("UTF-8"));
    break;
   case PluginResult.MESSAGE_TYPE_ARRAYBUFFER:
    result = new PluginResult(PluginResult.Status.OK, os.toByteArray());
    break;
   case PluginResult.MESSAGE_TYPE_BINARYSTRING:
    result = new PluginResult(PluginResult.Status.OK, os.toByteArray(), true);
    break;
   default: // Base64.
    byte[] base64 = Base64.encode(os.toByteArray(), Base64.NO_WRAP);
    String s = "data:" + contentType + ";base64," + new String(base64, "US-ASCII");
    result = new PluginResult(PluginResult.Status.OK, s);
  }

  return result;
 }
}
