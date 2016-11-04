package com.tencent.qcloud.xiaozhibo.logic;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import com.tencent.qcloud.xiaozhibo.base.TCConstants;
import com.tencent.qcloud.xiaozhibo.base.TCHttpEngine;
import com.tencent.upload.Const;
import com.tencent.upload.UploadManager;
import com.tencent.upload.task.ITask;
import com.tencent.upload.task.IUploadTaskListener;
import com.tencent.upload.task.UploadTask;
import com.tencent.upload.task.data.FileInfo;
import com.tencent.upload.task.impl.FileUploadTask;
import org.json.JSONObject;

import java.io.File;

/**
 * Cos人图片上传类
 */
public class TCUploadHelper {
    private static final String TAG = "TCUploadHelper";

    private final static int MAIN_CALL_BACK = 1;
    private final static int MAIN_PROCESS = 2;
    private final static int UPLOAD_AGAIN = 3;

    private Context mContext;
    private OnUploadListener mListerner;
    private Handler mMainHandler;

    public TCUploadHelper(final Context context, OnUploadListener listener) {
        mContext = context;
        mListerner = listener;

        mMainHandler = new Handler(Looper.getMainLooper(), new Handler.Callback() {
            @Override
            public boolean handleMessage(Message msg) {
                switch (msg.what) {
                    case MAIN_CALL_BACK:
                        if (mListerner != null) {
                            mListerner.onUploadResult(msg.arg1, (String) msg.obj);
                        }
                        break;
                    case UPLOAD_AGAIN:
                        Bundle taskBundle = (Bundle) msg.obj;
                        doUploadCover(taskBundle.getString("path",""),taskBundle.getString("sig",""),false);
                    default:
                        break;
                }
                return false;
            }
        });
    }

    private String createNetUrl() {
        return "/" + TCUserInfoMgr.getInstance().getUserId() + "_" + System.currentTimeMillis() + ".jpg";
    }

    private void doUploadCover(final String path, String sig, boolean uploadAgain) {
        UploadManager fileUploadMgr = new UploadManager(mContext, TCConstants.COS_APPID,
                Const.FileType.File, "qcloudphoto");
        Log.d(TAG,"uploadCover do upload path:"+path);
        String netUrl = createNetUrl();
        FileUploadTask task = new FileUploadTask(TCConstants.COS_BUCKET, path, netUrl, null, new IUploadTaskListener() {
            @Override
            public void onUploadSucceed(final FileInfo result) {
                Log.d(TAG,"uploadCover do upload sucess, url:"+result.url);
                Message msg = new Message();
                msg.what = MAIN_CALL_BACK;
                msg.arg1 = 0;
                msg.obj = result.url;

                mMainHandler.sendMessage(msg);
            }

            @Override
            public void onUploadFailed(int i, String s) {
                Log.w(TAG, "uploadCover do upload fail, error code:" + i + ",msg:" + s);
                Message msg = new Message();
                msg.what = MAIN_CALL_BACK;
                msg.arg1 = i;
                msg.obj = s;

                mMainHandler.sendMessage(msg);
            }

            @Override
            public void onUploadProgress(long l, long l1) {
                Log.d(TAG, "uploadCover do upload progress: " + l + "/" + l1);
            }

            @Override
            public void onUploadStateChange(ITask.TaskState taskState) {
                Log.d(TAG, "uploadCover do upload state change: " + taskState);
            }
        });

        task.setAuth(sig);
        if (!fileUploadMgr.upload(task)) {
            File file = new File(path);
            Log.w(TAG, "uploadCover start fail, file exists:"+file.exists()+", length:"+file.length());
            if (uploadAgain) {
                Log.w(TAG, "uploadCover start fail, try again after 1000ms");
                Bundle taskBundle = new Bundle();
                taskBundle.putString("path",path);
                taskBundle.putString("sig",sig);
                Message msg = new Message();
                msg.what = UPLOAD_AGAIN;
                msg.obj = taskBundle;

                mMainHandler.sendMessageDelayed(msg,1000);
            } else {
                Log.w(TAG, "uploadCover start fail");
                if (mListerner != null) {
                    mListerner.onUploadResult(-1,null);
                }
            }

        }
    }

    public void uploadCover(final String path) {
        JSONObject req = new JSONObject();
        try {
            req.put("Action","GetCOSSign");
        } catch (Exception e) {
            e.printStackTrace();
        }
        Log.d(TAG,"uploadCover get cos sig");
        TCHttpEngine.getInstance().post(req, new TCHttpEngine.Listener() {
            @Override
            public void onResponse(int retCode, String retMsg, JSONObject retData) {
                if (retCode == 0 && retData != null) {
                    try {
                        String sig = retData.getString("sign");
                        Log.d(TAG, "uploadCover got cos sig succeed");
                        doUploadCover(path, sig, true);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    if (mListerner != null) {
                        mListerner.onUploadResult(-1,null);
                    }
                    Log.d(TAG, "uploadCover got cos sig fail, error code:"+retCode);
                }

            }
        });
    }

    public interface OnUploadListener {
        public void onUploadResult(int code, String url);
    }
}
