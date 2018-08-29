package cn.id0755.im.activity;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.app.LoaderManager.LoaderCallbacks;

import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;

import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;


import net.openmob.mobileimsdk.android.core.LocalUDPDataSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

import cn.id0755.im.R;
import cn.id0755.im.manager.IMClientManager;
import cn.id0755.im.store.ConfigSp;

import static android.Manifest.permission.READ_CONTACTS;

/**
 * A login screen that offers login via email/password.
 */
public class LoginActivity extends AppCompatActivity implements LoaderCallbacks<Cursor> {

    private final static String TAG = "LoginActivity";
    /**
     * Id to identity READ_CONTACTS permission request.
     */
    private static final int REQUEST_READ_CONTACTS = 0;

    /**
     * A dummy authentication store containing known user names and passwords.
     * TODO: remove after connecting to a real authentication system.
     */
    private static final String[] DUMMY_CREDENTIALS = new String[]{
            "13510773022", "13510773033"
    };

    // UI references.
    private AutoCompleteTextView mPhoneView;
    private EditText mPasswordView;
    private View mProgressView;
    private View mLoginFormView;

    private CheckBox mAutoLogin;
    private CheckBox mRememberPassword;

    /**
     * 登陆进度提示
     */
    private OnLoginProgress onLoginProgress = null;
    /**
     * 收到服务端的登陆完成反馈时要通知的观察者（因登陆是异步实现，本观察者将由
     * ChatBaseEvent 事件的处理者在收到服务端的登陆反馈后通知之）
     */
    private Observer onLoginSucessObserver = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        // Set up the login form.
        initView();
        populateAutoComplete();

        //获取本机手机号码
//        String phoneNumber = PhoneUtils.getLocalPhoneNumber(this);
//        if (!TextUtils.isEmpty(phoneNumber)){
//            mPhoneView.setText(phoneNumber);
//        }
        mPhoneView.setText("13510773022");
        mPasswordView.setText("123456");


        // 登陆有关的初始化工作
        initForLogin();
    }

    private void initView(){
        mPhoneView = (AutoCompleteTextView) findViewById(R.id.phone);
        mLoginFormView = findViewById(R.id.login_form);
        mProgressView = findViewById(R.id.login_progress);
        mPasswordView = (EditText) findViewById(R.id.password);
        mPasswordView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                if (id == EditorInfo.IME_ACTION_DONE || id == EditorInfo.IME_NULL) {
                    attemptLogin();
                    return true;
                }
                return false;
            }
        });

        Button mEmailSignInButton = (Button) findViewById(R.id.email_sign_in_button);
        mEmailSignInButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                attemptLogin();
            }
        });

        mAutoLogin = findViewById(R.id.auto_login);
        mAutoLogin.setChecked(ConfigSp.getConfigSp().getAutoLogin());
        mAutoLogin.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                ConfigSp.getConfigSp().setAutoLogin(isChecked);
            }
        });
        mRememberPassword = findViewById(R.id.remember_password);
        mRememberPassword.setChecked(ConfigSp.getConfigSp().getRememberPsw());
        mRememberPassword.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                ConfigSp.getConfigSp().setRememberPsw(isChecked);
            }
        });
    }

    private void initForLogin() {
        // 实例化登陆进度提示封装类
        onLoginProgress = new OnLoginProgress(this);
        // 准备好异步登陆结果回调观察者（将在登陆方法中使用）
        onLoginSucessObserver = new Observer() {
            @Override
            public void update(Observable observable, Object data) {
                // * 已收到服务端登陆反馈则当然应立即取消显示登陆进度条
                onLoginProgress.showProgressing(false);
                // 服务端返回的登陆结果值
                int code = (Integer) data;
                // 登陆成功
                if (code == 0) {
                    //** 提示：登陆/连接 MobileIMSDK服务器成功后的事情在此实现即可

                    // 进入主界面
                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                    // 同时关闭登陆界面
                    finish();
                }
                // 登陆失败
                else {
                    new AlertDialog.Builder(LoginActivity.this)
                            .setTitle("友情提示")
                            .setMessage("Sorry，IM服务器连接失败，错误码=" + code)
                            .setPositiveButton("知道了", null)
                            .show();
                }
            }
        };
    }

    /**
     * 真正的登陆信息发送实现方法。
     */
    private void doLoginImpl() {
        // * 立即显示登陆处理进度提示（并将同时启动超时检查线程）
        onLoginProgress.showProgressing(true);
        // * 设置好服务端反馈的登陆结果观察者（当客户端收到服务端反馈过来的登陆消息时将被通知）
        IMClientManager.getInstance(this).getBaseEventListener()
                .setLoginOkForLaunchObserver(onLoginSucessObserver);

        // 异步提交登陆id和token
        new LocalUDPDataSender.SendLoginDataAsync(
                LoginActivity.this
                , mPhoneView.getText().toString().trim()
                , mPasswordView.getText().toString().trim()) {
            /**
             * 登陆信息发送完成后将调用本方法（注意：此处仅是登陆信息发送完成
             * ，真正的登陆结果要在异步回调中处理哦）。
             *
             * @param code 数据发送返回码，0 表示数据成功发出，否则是错误码
             */
            @Override
            protected void fireAfterSendLogin(int code) {
                if (code == 0) {
                    //
                    Toast.makeText(getApplicationContext(), "数据发送成功！", Toast.LENGTH_SHORT).show();
                    Log.d(MainActivity.class.getSimpleName(), "登陆/连接信息已成功发出！");
                } else {
                    Toast.makeText(getApplicationContext(), "数据发送失败。错误码是：" + code + "！", Toast.LENGTH_SHORT).show();

                    // * 登陆信息没有成功发出时当然无条件取消显示登陆进度条
                    onLoginProgress.showProgressing(false);
                }
            }
        }.execute();
    }

    /**
     * 获取APP版本信息.
     */
    private String getProgrammVersion() {
        PackageInfo info;
        try {
            info = getPackageManager().getPackageInfo(getPackageName(), 0);
//			versionCode = info.versionCode;
//			versionName = info.versionName;
            return info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "读程序版本信息时出错," + e.getMessage(), e);
            return "N/A";
        }
    }

    private boolean CheckNetworkState() {
        boolean flag = false;
        ConnectivityManager manager = (ConnectivityManager) getSystemService(
                Context.CONNECTIVITY_SERVICE);
        if (manager.getActiveNetworkInfo() != null) {
            flag = manager.getActiveNetworkInfo().isAvailable();
        }
        if (!flag) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setTitle("Network not avaliable");//
            builder.setMessage("Current network is not avaliable, set it?");//
            builder.setPositiveButton("Setting", new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)); //直接进入手机中的wifi网络设置界面
                }
            });
            builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            });
            builder.create();
            builder.show();
        }

        return flag;
    }

    /**
     * 登陆处理。
     *
     * @see #doLoginImpl()
     */
    private void doLogin() {
        if (!CheckNetworkState())
            return;

        // 发送登陆数据包
        if (mPhoneView.getText().toString().trim().length() > 0) {
            doLoginImpl();
        } else
            Log.e(MainActivity.class.getSimpleName()
                    , "txt.len=" + (mPhoneView.getText().toString().trim().length()));
    }

    /**
     * 登陆进度提示和超时检测封装实现类.
     */
    private class OnLoginProgress {
        /**
         * 登陆的超时时间定义
         */
        private final static int RETRY_DELAY = 6000;

        private Handler handler = null;
        private Runnable runnable = null;
        // 重试时要通知的观察者
        private Observer retryObsrver = null;

        private ProgressDialog progressDialogForPairing = null;
        private Activity parentActivity = null;

        public OnLoginProgress(Activity parentActivity) {
            this.parentActivity = parentActivity;
            init();
        }

        private void init() {
            progressDialogForPairing = new ProgressDialog(parentActivity);
            progressDialogForPairing.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            progressDialogForPairing.setTitle("登陆中");
            progressDialogForPairing.setMessage("正在登陆中，请稍候。。。");
            progressDialogForPairing.setCanceledOnTouchOutside(false);

            handler = new Handler();
            runnable = new Runnable() {
                @Override
                public void run() {
                    onTimeout();
                }
            };
        }

        /**
         * 登陆超时后要调用的方法。
         */
        private void onTimeout() {
            // 本观察者中由用户选择是否重试登陆或者取消登陆重试
            new AlertDialog.Builder(LoginActivity.this)
                    .setTitle("超时了")
                    .setMessage("登陆超时，可能是网络故障或服务器无法连接，是否重试？")
                    .setPositiveButton("重试！", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // 确认要重试时（再次尝试登陆哦）
                            doLogin();
                        }
                    })
                    .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // 不需要重试则要停止“登陆中”的进度提示哦
                            OnLoginProgress.this.showProgressing(false);
                        }
                    })
                    .show();
        }

        /**
         * 显示进度提示.
         *
         * @param show
         */
        public void showProgressing(boolean show) {
            // 显示进度提示的同时即启动超时提醒线程
            if (show) {
                showLoginProgressGUI(true);

                // 先无论如何保证利重试检测线程在启动前肯定是处于停止状态
                handler.removeCallbacks(runnable);
                // 启动
                handler.postDelayed(runnable, RETRY_DELAY);
            }
            // 关闭进度提示
            else {
                // 无条件停掉延迟重试任务
                handler.removeCallbacks(runnable);

                showLoginProgressGUI(false);
            }
        }

        /**
         * 进度提示时要显示或取消显示的GUI内容。
         *
         * @param show true表示显示gui内容，否则表示结速gui内容显示
         */
        private void showLoginProgressGUI(boolean show) {
            // 显示登陆提示信息
            if (show) {
                try {
                    if (parentActivity != null && !parentActivity.isFinishing())
                        progressDialogForPairing.show();
                } catch (WindowManager.BadTokenException e) {
                    Log.e(TAG, e.getMessage(), e);
                }
            }
            // 关闭登陆提示信息
            else {
                // 此if语句是为了保证延迟线程里不会因Activity已被关闭而此处却要非法地执行show的情况（此判断可趁为安全的show方法哦！）
                if (parentActivity != null && !parentActivity.isFinishing())
                    progressDialogForPairing.dismiss();
            }
        }
    }

    private void populateAutoComplete() {
        if (!mayRequestContacts()) {
            return;
        }

        getLoaderManager().initLoader(0, null, this);
    }

    private boolean mayRequestContacts() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        if (checkSelfPermission(READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        if (shouldShowRequestPermissionRationale(READ_CONTACTS)) {
            Snackbar.make(mPhoneView, R.string.permission_rationale, Snackbar.LENGTH_INDEFINITE)
                    .setAction(android.R.string.ok, new View.OnClickListener() {
                        @Override
                        @TargetApi(Build.VERSION_CODES.M)
                        public void onClick(View v) {
                            requestPermissions(new String[]{READ_CONTACTS}, REQUEST_READ_CONTACTS);
                        }
                    });
        } else {
            requestPermissions(new String[]{READ_CONTACTS}, REQUEST_READ_CONTACTS);
        }
        return false;
    }

    /**
     * Callback received when a permissions request has been completed.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == REQUEST_READ_CONTACTS) {
            if (grantResults.length == 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                populateAutoComplete();
            }
        }
    }


    /**
     * Attempts to sign in or register the account specified by the login form.
     * If there are form errors (invalid email, missing fields, etc.), the
     * errors are presented and no actual login attempt is made.
     */
    private void attemptLogin() {
        // Reset errors.
        mPhoneView.setError(null);
        mPasswordView.setError(null);

        // Store values at the time of the login attempt.
        String phone = mPhoneView.getText().toString();
        String password = mPasswordView.getText().toString();

        boolean cancel = false;
        View focusView = null;

        // Check for a valid password, if the user entered one.
        if (!TextUtils.isEmpty(password) && password.length() < 6) {
            mPasswordView.setError(getString(R.string.error_invalid_password));
            focusView = mPasswordView;
            cancel = true;
        }

        // Check for a valid email address.
        if (TextUtils.isEmpty(phone)) {
            mPhoneView.setError(getString(R.string.error_field_required));
            focusView = mPhoneView;
            cancel = true;
        } else if (!PhoneNumberUtils.isGlobalPhoneNumber(phone)) {
            mPhoneView.setError(getString(R.string.error_invalid_phone));
            focusView = mPhoneView;
            cancel = true;
        }

        if (cancel) {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            focusView.requestFocus();
        } else {
            // Show a progress spinner, and kick off a background task to
            // perform the user login attempt.
            doLogin();
        }
    }

    /**
     * Shows the progress UI and hides the login form.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    private void showProgress(final boolean show) {
        // On Honeycomb MR2 we have the ViewPropertyAnimator APIs, which allow
        // for very easy animations. If available, use these APIs to fade-in
        // the progress spinner.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

            mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
            mLoginFormView.animate().setDuration(shortAnimTime).alpha(
                    show ? 0 : 1).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
                }
            });

            mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            mProgressView.animate().setDuration(shortAnimTime).alpha(
                    show ? 1 : 0).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
                }
            });
        } else {
            // The ViewPropertyAnimator APIs are not available, so simply show
            // and hide the relevant UI components.
            mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        return new CursorLoader(this,
                // Retrieve data rows for the device user's 'profile' contact.
                Uri.withAppendedPath(ContactsContract.Profile.CONTENT_URI,
                        ContactsContract.Contacts.Data.CONTENT_DIRECTORY), ProfileQuery.PROJECTION,

                // Select only email addresses.
                ContactsContract.Contacts.Data.MIMETYPE +
                        " = ?", new String[]{ContactsContract.CommonDataKinds.Phone
                .CONTENT_ITEM_TYPE},

                // Show primary email addresses first. Note that there won't be
                // a primary email address if the user hasn't specified one.
                ContactsContract.Contacts.Data.IS_PRIMARY + " DESC");
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        List<String> emails = new ArrayList<>();
        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            emails.add(cursor.getString(ProfileQuery.ADDRESS));
            cursor.moveToNext();
        }

        addEmailsToAutoComplete(emails);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {

    }

    private void addEmailsToAutoComplete(List<String> phoneCollection) {
        //Create adapter to tell the AutoCompleteTextView what to show in its dropdown list.
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(LoginActivity.this,
                        android.R.layout.simple_dropdown_item_1line, phoneCollection);

        mPhoneView.setAdapter(adapter);
    }


    private interface ProfileQuery {
        String[] PROJECTION = {
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.IS_PRIMARY,
        };

        int ADDRESS = 0;
        int IS_PRIMARY = 1;
    }
}

