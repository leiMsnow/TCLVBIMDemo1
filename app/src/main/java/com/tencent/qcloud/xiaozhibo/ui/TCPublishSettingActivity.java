package com.tencent.qcloud.xiaozhibo.ui;

import android.Manifest;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestManager;
import com.tencent.qcloud.xiaozhibo.R;
import com.tencent.qcloud.xiaozhibo.base.TCConstants;
import com.tencent.qcloud.xiaozhibo.base.TCUtils;
import com.tencent.qcloud.xiaozhibo.logic.ITCUserInfoMgrListener;
import com.tencent.qcloud.xiaozhibo.logic.TCLocationHelper;
import com.tencent.qcloud.xiaozhibo.logic.TCUploadHelper;
import com.tencent.qcloud.xiaozhibo.logic.TCUserInfoMgr;
import com.tencent.qcloud.xiaozhibo.ui.customviews.TCCustomSwitch;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class TCPublishSettingActivity extends TCBaseActivity implements View.OnClickListener, TCUploadHelper.OnUploadListener, TCLocationHelper.OnLocationListener {
    private TextView BtnBack, BtnPublish;
    private Dialog mPicChsDialog;
    private ImageView cover;
    private Uri fileUri, cropUri;
    private TextView tvPicTip;
    private TextView tvLBS;
    private TCCustomSwitch btnLBS;
    private TextView tvTitle;
    private TCUploadHelper mUploadHelper;

    private static final int CAPTURE_IMAGE_CAMERA = 100;
    private static final int IMAGE_STORE = 200;
    private static final String TAG = TCPublishSettingActivity.class.getSimpleName();

    private static final int CROP_CHOOSE = 10;
    private boolean mUploading = false;
    private boolean mPermission = false;

    private RadioGroup mRGBitrate;
    private RadioGroup mRGRecordType;
//    private RadioGroup mRGOrientation;
    private RelativeLayout mRLBitrate;
    private int mRecordType = TCConstants.RECORD_TYPE_CAMERA;
    private int mBitrateType = TCConstants.BITRATE_NORMAL;
//    private int mOrientation = TCConstants.ORIENTATION_LANDSCAPE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_publish_setting);
        mUploadHelper = new TCUploadHelper(this, this);

        tvTitle = (TextView) findViewById(R.id.live_title);
        BtnBack = (TextView) findViewById(R.id.btn_cancel);
        tvPicTip = (TextView) findViewById(R.id.tv_pic_tip);
        BtnPublish = (TextView) findViewById(R.id.btn_publish);
        cover = (ImageView) findViewById(R.id.cover);
        tvLBS = (TextView) findViewById(R.id.address);
        btnLBS = (TCCustomSwitch) findViewById(R.id.btn_lbs);


        mRGRecordType = (RadioGroup) findViewById(R.id.rg_record_type);
        mRGBitrate = (RadioGroup) findViewById(R.id.rg_bitrate);
        mRLBitrate= (RelativeLayout) findViewById(R.id.rl_bitrate);

        mRGRecordType.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                switch (checkedId) {
                    case R.id.rb_record_camera:
                        mRecordType = TCConstants.RECORD_TYPE_CAMERA;
                        mRLBitrate.setVisibility(View.GONE);
                        break;
                    case R.id.rb_record_screen:
                        if (!checkScrRecordPermission()) {
                            Toast.makeText(getApplicationContext(), "当前安卓系统版本过低，仅支持5.0及以上系统", Toast.LENGTH_SHORT).show();
                            mRGRecordType.check(R.id.rb_record_camera);
                            return;
                        }
                        try {
                            TCUtils.checkFloatWindowPermission(TCPublishSettingActivity.this);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        mRLBitrate.setVisibility(View.VISIBLE);
                        mRecordType = TCConstants.RECORD_TYPE_SCREEN;
                        break;
                    default:
                        break;
                }
            }
        });

        mRGBitrate.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                switch (checkedId) {
                    case R.id.rb_bitrate_slow:
                        mBitrateType = TCConstants.BITRATE_SLOW;
                        break;
                    case R.id.rb_bitrate_normal:
                        mBitrateType = TCConstants.BITRATE_NORMAL;
                        break;
                    case R.id.rb_bitrate_fast:
                        mBitrateType = TCConstants.BITRATE_FAST;
                        break;
                    default:
                        break;
                }
            }
        });

        cover.setOnClickListener(this);
        BtnBack.setOnClickListener(this);
        BtnPublish.setOnClickListener(this);
        btnLBS.setOnClickListener(this);

        initPhotoDialog();

        mPermission = checkPublishPermission();

        String strCover = TCUserInfoMgr.getInstance().getCoverPic();
        if(!TextUtils.isEmpty(strCover)) {
            RequestManager req = Glide.with(this);
            req.load(strCover).into(cover);
            tvPicTip.setVisibility(View.GONE);
        } else {
            cover.setImageResource(R.drawable.publish_background);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_cancel:
                finish();
                break;
            case R.id.btn_publish:
                //trim避免空格字符串
                if (TextUtils.isEmpty(tvTitle.getText().toString().trim())) {
                    Toast.makeText(getApplicationContext(), "请输入非空直播标题", Toast.LENGTH_SHORT).show();
                } else if(TCUtils.getCharacterNum(tvTitle.getText().toString()) > TCConstants.TV_TITLE_MAX_LEN){
                    Toast.makeText(getApplicationContext(), "直播标题过长 ,最大长度为"+TCConstants.TV_TITLE_MAX_LEN/2, Toast.LENGTH_SHORT).show();
                } else if (mUploading) {
                    Toast.makeText(getApplicationContext(), getString(R.string.publish_wait_uploading), Toast.LENGTH_SHORT).show();
                } else if(!TCUtils.isNetworkAvailable(this)) {
                    Toast.makeText(getApplicationContext(), "当前网络环境不能发布直播", Toast.LENGTH_SHORT).show();
                } else {

                    if (mRecordType == TCConstants.RECORD_TYPE_SCREEN) {
                        //录屏
                        Intent intent = new Intent(this, TCScreenRecordActivity.class);
                        intent.putExtra(TCConstants.ROOM_TITLE,
                                TextUtils.isEmpty(tvTitle.getText().toString()) ? TCUserInfoMgr.getInstance().getNickname() : tvTitle.getText().toString());
                        intent.putExtra(TCConstants.USER_ID, TCUserInfoMgr.getInstance().getUserId());
                        intent.putExtra(TCConstants.USER_NICK, TCUserInfoMgr.getInstance().getNickname());
                        intent.putExtra(TCConstants.USER_HEADPIC, TCUserInfoMgr.getInstance().getHeadPic());
                        intent.putExtra(TCConstants.COVER_PIC, TCUserInfoMgr.getInstance().getCoverPic());
//                        intent.putExtra(TCConstants.SCR_ORIENTATION, mOrientation);
                        intent.putExtra(TCConstants.BITRATE, mBitrateType);
                        intent.putExtra(TCConstants.USER_LOC,
                                tvLBS.getText().toString().equals(getString(R.string.text_live_lbs_fail)) ||
                                        tvLBS.getText().toString().equals(getString(R.string.text_live_location)) ?
                                        getString(R.string.text_live_close_lbs) : tvLBS.getText().toString());
                        startActivity(intent);
                        finish();
                    } else {
                        //摄像头
                        Intent intent = new Intent(this, TCLivePublisherActivity.class);
                        intent.putExtra(TCConstants.ROOM_TITLE,
                                TextUtils.isEmpty(tvTitle.getText().toString()) ? TCUserInfoMgr.getInstance().getNickname() : tvTitle.getText().toString());
                        intent.putExtra(TCConstants.USER_ID, TCUserInfoMgr.getInstance().getUserId());
                        intent.putExtra(TCConstants.USER_NICK, TCUserInfoMgr.getInstance().getNickname());
                        intent.putExtra(TCConstants.USER_HEADPIC, TCUserInfoMgr.getInstance().getHeadPic());
                        intent.putExtra(TCConstants.COVER_PIC, TCUserInfoMgr.getInstance().getCoverPic());
                        intent.putExtra(TCConstants.USER_LOC,
                                tvLBS.getText().toString().equals(getString(R.string.text_live_lbs_fail)) ||
                                        tvLBS.getText().toString().equals(getString(R.string.text_live_location)) ?
                                        getString(R.string.text_live_close_lbs) : tvLBS.getText().toString());
                        startActivity(intent);
                        finish();
                    }

                }
                break;
            case R.id.cover:
                mPicChsDialog.show();
                break;
            case R.id.btn_lbs:
                if (btnLBS.getChecked()) {
                    btnLBS.setChecked(false, true);
                    tvLBS.setText(R.string.text_live_close_lbs);
                } else {
                    btnLBS.setChecked(true, true);
                    tvLBS.setText(R.string.text_live_location);
                    if (TCLocationHelper.checkLocationPermission(this)) {
                        if (!TCLocationHelper.getMyLocation(this, this)) {
                            tvLBS.setText(getString(R.string.text_live_lbs_fail));
                            //Toast.makeText(getApplicationContext(), "定位失败，请查看是否打开GPS", Toast.LENGTH_SHORT).show();
                            btnLBS.setChecked(false, false);
                        }
                    }
                }
                break;
        }
    }

    /**
     * 图片选择对话框
     */
    private void initPhotoDialog() {
        mPicChsDialog = new Dialog(this, R.style.floag_dialog);
        mPicChsDialog.setContentView(R.layout.dialog_pic_choose);

        WindowManager windowManager = getWindowManager();
        Display display = windowManager.getDefaultDisplay();
        Window dlgwin = mPicChsDialog.getWindow();
        WindowManager.LayoutParams lp = dlgwin.getAttributes();
        dlgwin.setGravity(Gravity.BOTTOM);
        lp.width = (int) (display.getWidth()); //设置宽度

        mPicChsDialog.getWindow().setAttributes(lp);

        TextView camera = (TextView) mPicChsDialog.findViewById(R.id.chos_camera);
        TextView picLib = (TextView) mPicChsDialog.findViewById(R.id.pic_lib);
        TextView cancel = (TextView) mPicChsDialog.findViewById(R.id.btn_cancel);
        camera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getPicFrom(CAPTURE_IMAGE_CAMERA);
                mPicChsDialog.dismiss();
            }
        });

        picLib.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getPicFrom(IMAGE_STORE);
                mPicChsDialog.dismiss();
            }
        });
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPicChsDialog.dismiss();
            }
        });
    }


    /**
     * 获取图片资源
     *
     * @param type 类型（本地IMAGE_STORE/拍照CAPTURE_IMAGE_CAMERA）
     */
    private void getPicFrom(int type) {
        if (!mPermission) {
            Toast.makeText(this, getString(R.string.tip_no_permission), Toast.LENGTH_SHORT).show();
            return;
        }

        switch (type) {
            case CAPTURE_IMAGE_CAMERA:
                fileUri = createCoverUri("");
                Intent intent_photo = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                intent_photo.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
                startActivityForResult(intent_photo, CAPTURE_IMAGE_CAMERA);
                break;
            case IMAGE_STORE:
                fileUri = createCoverUri("_select");
                Intent intent_album = new Intent("android.intent.action.GET_CONTENT");
                intent_album.setType("image/*");
                startActivityForResult(intent_album, IMAGE_STORE);
                break;

        }
    }

    private boolean checkPublishPermission() {
        if (Build.VERSION.SDK_INT >= 23) {
            List<String> permissions = new ArrayList<>();
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(TCPublishSettingActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(TCPublishSettingActivity.this, Manifest.permission.CAMERA)) {
                permissions.add(Manifest.permission.CAMERA);
            }
            if (PackageManager.PERMISSION_GRANTED != ActivityCompat.checkSelfPermission(TCPublishSettingActivity.this, Manifest.permission.READ_PHONE_STATE)) {
                permissions.add(Manifest.permission.READ_PHONE_STATE);
            }
            if (permissions.size() != 0) {
                ActivityCompat.requestPermissions(TCPublishSettingActivity.this,
                        permissions.toArray(new String[0]),
                        TCConstants.WRITE_PERMISSION_REQ_CODE);
                return false;
            }
        }

        return true;
    }

    private boolean checkScrRecordPermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    private Uri createCoverUri(String type) {
        String filename = TCUserInfoMgr.getInstance().getUserId() + type + ".jpg";
        String path = Environment.getExternalStorageDirectory()+ "/xiaozhibo";

        File outputImage = new File(path, filename);
        if (ContextCompat.checkSelfPermission(TCPublishSettingActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(TCPublishSettingActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, TCConstants.WRITE_PERMISSION_REQ_CODE);
            return null;
        }
        try {
            File pathFile = new File(path);
            if (!pathFile.exists()) {
                pathFile.mkdirs();
            }
            if (outputImage.exists()) {
                outputImage.delete();
            }
//            outputImage.createNewFile();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "生成封面失败", Toast.LENGTH_SHORT).show();
        }

        return Uri.fromFile(outputImage);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case CAPTURE_IMAGE_CAMERA:
                    startPhotoZoom(fileUri);
                    break;
                case IMAGE_STORE:
                    String path = TCUtils.getPath(this, data.getData());
                    if (null != path) {
                        Log.d(TAG, "startPhotoZoom->path:" + path);
                        File file = new File(path);
                        startPhotoZoom(Uri.fromFile(file));
                    }
                    break;
                case CROP_CHOOSE:
                    mUploading = true;
                    tvPicTip.setVisibility(View.GONE);
//                    cover.setImageBitmap(null);
//                    cover.setImageURI(cropUri);
                    mUploadHelper.uploadCover(cropUri.getPath());

                    break;

            }
        }

    }

    public void startPhotoZoom(Uri uri) {
        cropUri = createCoverUri("_crop");

        Intent intent = new Intent("com.android.camera.action.CROP");
        intent.setDataAndType(uri, "image/*");
        intent.putExtra("crop", "true");
        intent.putExtra("aspectX", 750);
        intent.putExtra("aspectY", 550);
        intent.putExtra("outputX", 750);
        intent.putExtra("outputY", 550);
        intent.putExtra("scale", true);
        intent.putExtra("return-data", false);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, cropUri);
        intent.putExtra("outputFormat", Bitmap.CompressFormat.JPEG.toString());
        startActivityForResult(intent, CROP_CHOOSE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,@NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case TCConstants.LOCATION_PERMISSION_REQ_CODE:
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (!TCLocationHelper.getMyLocation(this, this)) {
                        tvLBS.setText(getString(R.string.text_live_lbs_fail));
                        btnLBS.setChecked(false, false);
                    }
                }
                break;
            case TCConstants.WRITE_PERMISSION_REQ_CODE:
                for (int ret : grantResults) {
                    if (ret != PackageManager.PERMISSION_GRANTED) {
                        return;
                    }
                }
                mPermission = true;
                break;
            default:
                break;
        }
    }

    @Override
    public void onLocationChanged(int code, double lat1, double long1, String location) {
        if (btnLBS.getChecked()) {
            if (0 == code) {
                tvLBS.setText(location);
                TCUserInfoMgr.getInstance().setLocation(location, lat1, long1, new ITCUserInfoMgrListener() {
                    @Override
                    public void OnQueryUserInfo(int error, String errorMsg) {
                        // TODO: 16/8/10
                    }

                    @Override
                    public void OnSetUserInfo(int error, String errorMsg) {
                        if (0 != error) Toast.makeText(getApplicationContext(),"设置位置失败" + errorMsg,Toast.LENGTH_LONG).show();
                    }
                });
            } else {
                tvLBS.setText(getString(R.string.text_live_lbs_fail));
            }
        } else {
            TCUserInfoMgr.getInstance().setLocation("", 0, 0, new ITCUserInfoMgrListener() {
                @Override
                public void OnQueryUserInfo(int error, String errorMsg) {
                    // TODO: 16/8/10
                }

                @Override
                public void OnSetUserInfo(int error, String errorMsg) {
                    if (0 != error) Toast.makeText(getApplicationContext(),"设置位置失败" + errorMsg,Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    @Override
    public void onUploadResult(int code, String url) {

        if (0 == code) {
            TCUserInfoMgr.getInstance().setUserCoverPic(url, new ITCUserInfoMgrListener() {
                @Override
                public void OnQueryUserInfo(int error, String errorMsg) {
                    // TODO: 16/8/10
                }

                @Override
                public void OnSetUserInfo(int error, String errorMsg) {

                }
            });
            RequestManager req = Glide.with(this);
            req.load(url).into(cover);
            Toast.makeText(this, "上传封面成功", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "上传封面失败，错误码 "+code, Toast.LENGTH_SHORT).show();
        }
        mUploading = false;
    }

}
