package io.lpin.android.sdk.face.extenstions;

import android.content.pm.PackageManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

public class PermissionCheckFragment extends Fragment {
    protected boolean checkPermissions(String[] neededPermissions) {
        if (neededPermissions == null || neededPermissions.length == 0) {
            return true;
        }
        boolean allGranted = true;
        for (String neededPermission : neededPermissions) {
            allGranted &= ContextCompat.checkSelfPermission(requireActivity(), neededPermission) == PackageManager.PERMISSION_GRANTED;
        }
        return allGranted;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean isAllGranted = true;
        for (int grantResult : grantResults) {
            isAllGranted &= (grantResult == PackageManager.PERMISSION_GRANTED);
        }
        onRequestPermissionResult(requestCode, isAllGranted);
    }

    protected void onRequestPermissionResult(int requestCode, boolean isAllGranted) {

    }

    protected void showToast(final String s) {
        this.getActivity().runOnUiThread(() -> Toast.makeText(this.getActivity(), s, Toast.LENGTH_SHORT).show());
    }
}
