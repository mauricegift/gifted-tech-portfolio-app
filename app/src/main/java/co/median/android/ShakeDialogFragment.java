package co.median.android;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class ShakeDialogFragment extends DialogFragment {
    public interface ShakeDialogListener {
        public void onClearCache(DialogFragment dialog);
    }

    ShakeDialogListener listener;

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity(), R.style.AlertDialogTheme);
        builder.setTitle(R.string.shake_to_clear_cache)
                .setItems(R.array.device_shaken_options, (dialogInterface, i) -> {
                    if (i == 0) {
                        listener.onClearCache(ShakeDialogFragment.this);
                    }
                });
        return builder.create();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        try {
            listener = (ShakeDialogListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() + " must implement ShakeDialogListener");
        }
    }
}
