package com.eliberty.plugin.payzen;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.app.Activity;
import android.app.Application;
import android.util.Log;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import android.graphics.Color;
import com.lyra.mpos.domain.payment.MposTransactionType;
import com.lyra.mpos.sdk.MCurrency;
import com.lyra.mpos.sdk.MCustomer;
import com.lyra.mpos.sdk.MTransaction;
import com.lyra.mpos.sdk.MposResult;
import com.lyra.mpos.sdk.MposSDK;
import com.lyra.mpos.sdk.error.MposException;
import com.lyra.mpos.sdk.process.manager.Result;

/**
 * CordovaPayzen is a PhoneGap/Cordova plugin that bridges Android intents and MposSDK
 *
 * @author lmenu@eliberty.fr
 *
 */
public class CordovaPayzen extends CordovaPlugin
{
    private Activity activity;
    private static final String START_ACTIVITY = "startActivity";
    private static final String TOUCH_INIT_MPOS_IN_ERROR = "TOUCH_INIT_MPOS_IN_ERROR";
    private static final String TOUCH_CARD_READER_NOT_AVAILABLE = "TOUCH_CARD_READER_NOT_AVAILABLE";
    private static final String TOUCH_SDK_NOT_READY = "TOUCH_SDK_NOT_READY";
    private CallbackContext callbackContext = null;
    private String token;
    private String acceptorId;
    private String label;
    private String email;
    private Long amount;
    private String orderId;

    /**
     * Method witch permit to initialize the Cordova Payzen Plugin
     */
    @Override
    protected void pluginInitialize()
    {
        activity = this.cordova.getActivity();
        Application application = activity.getApplication();
        try {
            Log.i("eliberty.plugin.payzen", "pluginInitialize");
            MposSDK.init(application);
            MposSDK.setThemeColor(Color.parseColor("#F98253"));
        }
        catch (MposException e) {
            Log.w("eliberty.plugin.payzen", "TOUCH_INIT_MPOS_IN_ERROR");
            runCallbackError(TOUCH_INIT_MPOS_IN_ERROR, TOUCH_INIT_MPOS_IN_ERROR);
        }
    }

    /**
     * Executes the request.
     * https://github.com/apache/cordova-android/blob/master/framework/src/org/apache/cordova/CordovaPlugin.java
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
     *
     */
    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext)
    {
        Log.i("eliberty.plugin.payzen", "execute Cordova");
        this.callbackContext = callbackContext;
        final JSONArray finalArgs = args;

        if (action.equals(START_ACTIVITY)) {
            cordova.getThreadPool().execute(new Runnable() {
                public void run() {
                    try {
                        JSONObject obj = finalArgs.getJSONObject(0);
                        token = obj.has("token") ? obj.getString("token") : null;
                        acceptorId = obj.has("acceptorId") ? obj.getString("acceptorId") : null;
                        label = obj.has("label") ? obj.getString("label") : null;
                        email = obj.has("email") ? obj.getString("email") : null;
                        amount = Long.parseLong(obj.has("amount") ? obj.getString("amount") : "0");
                        orderId = obj.has("orderId") ? obj.getString("orderId") : null;

                        startActivity(0);
                    }
                    catch (JSONException ex) {
                        Log.w("eliberty.plugin.payzen", "JSONException: " + ex.getMessage());
                    }
                }
            });
        }

        return true;
    }


    /**
     * Register token and start the mpos activity
     *
     * @param nbAttempts Number of attempts. When attempts is 10 we launch callback error
     */
    private void startActivity(Integer nbAttempts)
    {
        try {
            Log.i("eliberty.plugin.payzen", "registerToken");
            MposSDK.registerToken(this.token);

            if (MposSDK.isCardReaderAvailable()) {
                Log.i("eliberty.plugin.payzen", "isCardReaderAvailable");
                MposResult mposResult = MposSDK.start(activity, this.acceptorId);
                Log.i("eliberty.plugin.payzen", "start SDK");
                mposResult.setCallback(new MposResult.ResultCallback() {
                    @Override
                    public void onSuccess(Result result) {
                        launchSuccessStartActivity();
                    }

                    @Override
                    public void onError(Result error) {
                        runCallbackError(error.getCode(), error.getMessage());
                    }

                    @Override
                    public void onError(Throwable e) {
                        runCallbackError(Integer.toString(e.hashCode()), e.getMessage());
                    }
                });
            } else if (nbAttempts < 10) {
                Log.i("eliberty.plugin.payzen", "not ready start SDK");
                Thread.sleep(5000);
                nbAttempts++;
                startActivity(nbAttempts);
            } else {
                runCallbackError(TOUCH_CARD_READER_NOT_AVAILABLE, TOUCH_CARD_READER_NOT_AVAILABLE);
            }
        }
        catch (MposException e) {
            runCallbackError(e.getTypeException(), e.getMessage());
        }
        catch(InterruptedException ex) {
            runCallbackError(Integer.toString(ex.hashCode()), ex.getMessage());
        }
    }

    /**
     *  Prepare the mTransaction object and execute transaction is SDK is ready
     */
    private void launchSuccessStartActivity()
    {
        Log.i("eliberty.plugin.payzen", "launchSuccessStartActivity");
        MCurrency currency = MposSDK.getDefaultCurrencies();

        MCustomer mposCustomer = new MCustomer();
        mposCustomer.setEmail(email);

        MTransaction mTransaction = new MTransaction();
        mTransaction.setAmount(amount);
        mTransaction.setCurrency(currency);
        mTransaction.setOrderId(orderId);
        mTransaction.setOperationType(MposTransactionType.DEBIT);
        mTransaction.setCustomer(mposCustomer);
        mTransaction.setOrderInfo(label);

        if (MposSDK.isReady()) {
            Log.i("eliberty.plugin.payzen", "SDK is ready");
            executeTransaction(mTransaction);
        } else {
            Log.i("eliberty.plugin.payzen", "SDK is not ready !");
            runCallbackError(TOUCH_SDK_NOT_READY, TOUCH_SDK_NOT_READY);
        }
    }

    /**
     * Execute the transaction with the mpos
     *
     * @param mTransaction The object transaction
     */
    private void executeTransaction(MTransaction mTransaction)
    {
        try {
            Log.i("eliberty.plugin.payzen", "executeTransaction");
            MposResult mposResult = MposSDK.executeTransaction(activity, mTransaction, false);

            mposResult.setCallback(new MposResult.ResultCallback() {
                @Override
                public void onSuccess(Result result) {
                    runCallbackSuccess(result);
                }
                @Override
                public void onError(Result error) {
                    runCallbackError(error.getCode(), error.getMessage());
                }
                @Override
                public void onError(Throwable e) {
                    runCallbackError(Integer.toString(e.hashCode()), e.getMessage());
                }
            });
        }
        catch (MposException e) {
            Log.w("eliberty.plugin.payzen", "MposException : " + e.getMessage());
            runCallbackError(e.getTypeException(), e.getMessage());
        }
    }

    /**
     * Return a Json object for the cordova's callback in case of mpos success
     * @param result The result of Transaction
     */
    private void runCallbackSuccess(Result result)
    {
        try {
            Log.i("eliberty.plugin.payzen", "call success callback runCallbackSuccess");
            JSONObject obj = new JSONObject();
            obj.put("transactionId", result.getTransaction().getTransactionId());
            obj.put("status", result.getTransaction().getTransactionStatusLabel().toString());
            obj.put("receipt", result.getTransaction().getReceipt());
            obj.put("transactionDate", result.getTransaction().getSubmissionDate());
            callbackContext.success(obj);
        }
        catch (JSONException jse) {
            Log.w("eliberty.plugin.payzen", "JSONException : " + jse.getMessage());
            runCallbackError(Integer.toString(jse.hashCode()), jse.getMessage());
        }
    }

    /**
     * Return a Json object for the cordova's callback in case of mpos error
     *
     * @param code The code error
     * @param message The message error
     */
    private void runCallbackError(String code, String message)
    {
        try {
            Log.i("eliberty.plugin.payzen", "call error callback runCallbackError");
            JSONObject obj = new JSONObject();
            obj.put("code", code);
            obj.put("message", message);
            callbackContext.error(obj);
        }
        catch (JSONException jse) {
            Log.w("eliberty.plugin.payzen", "JSONException : " + jse.getMessage());
            runCallbackError(Integer.toString(jse.hashCode()), jse.getMessage());
        }
    }

    /**
     * On destroy, we must remove all callback and destroy app
     */
    @Override
    public void onDestroy()
    {
        Log.i("eliberty.plugin.payzen", "shutdown MposSDK");
        try {
            MposSDK.shutdown();
        }
        catch (MposException e) {
            Log.w("eliberty.plugin.payzen", "MposException : " + e.getMessage());
            runCallbackError(e.getTypeException(), e.getMessage());
        }
    }
}