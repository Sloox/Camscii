package wrightstuff.camerautils.application;

import android.app.Application;

import net.ralphpina.permissionsmanager.PermissionsManager;

/**
 * Created by michaelwright on 01/12/2017.
 */

public class CameraApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        PermissionsManager.init(this);
    }

}
