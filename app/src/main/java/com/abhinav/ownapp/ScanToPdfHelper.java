package com.abhinav.ownapp;

import android.app.Activity; import android.content.ContentValues;
import android.net.Uri; import android.os.Build; import android.provider.MediaStore; import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher; import androidx.activity.result.IntentSenderRequest; import androidx.activity.result.contract.ActivityResultContracts; import androidx.appcompat.app.AppCompatActivity;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner; import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions; import com.google.mlkit.vision.documentscanner.GmsDocumentScanning; import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult;
import java.io.InputStream; import java.io.OutputStream;

@SuppressWarnings("all")
public class ScanToPdfHelper {
    private final AppCompatActivity activity; private ActivityResultLauncher<IntentSenderRequest> scannerLauncher; private OnScanCompletedListener listener;

    public interface OnScanCompletedListener { void onPdfCreated(Uri pdfUri, int pageCount); void onScanCancelled(); void onScanError(Exception e); }

    public ScanToPdfHelper(AppCompatActivity activity) { this.activity = activity; initLauncher(); }
    public void setOnScanCompletedListener(OnScanCompletedListener listener) { this.listener = listener; }

    private void initLauncher() {
        scannerLauncher = activity.registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                GmsDocumentScanningResult scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.getData());
                if (scanResult != null && scanResult.getPdf() != null) {
                    Uri tempPdfUri = scanResult.getPdf().getUri(); int pageCount = scanResult.getPdf().getPageCount();
                    Uri savedPdfUri = persistPdfToMediaStore(tempPdfUri);
                    if (listener != null) listener.onPdfCreated(savedPdfUri != null ? savedPdfUri : tempPdfUri, pageCount);
                } else if (listener != null) { listener.onScanCancelled(); }
            } else if (result.getResultCode() == Activity.RESULT_CANCELED && listener != null) { listener.onScanCancelled(); }
        });
    }

    public void startScanToPdf(int maxPages) {
        try {
            GmsDocumentScannerOptions options = new GmsDocumentScannerOptions.Builder().setGalleryImportAllowed(true).setPageLimit(maxPages > 0 ? maxPages : 50).setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF).setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL).build();
            GmsDocumentScanner scanner = GmsDocumentScanning.getClient(options);
            scanner.getStartScanIntent(activity).addOnSuccessListener(intentSender -> {
                scannerLauncher.launch(new IntentSenderRequest.Builder(intentSender).build());
            }).addOnFailureListener(e -> {
                Toast.makeText(activity, "Failed to launch scanner: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                if (listener != null) listener.onScanError(e);
            });
        } catch (Exception e) { if (listener != null) listener.onScanError(e); }
    }

    private Uri persistPdfToMediaStore(Uri tempUri) {
        try {
            InputStream is = activity.getContentResolver().openInputStream(tempUri); if (is == null) return null;
            String fileName = "Scanned_Doc_" + System.currentTimeMillis() + ".pdf";
            ContentValues values = new ContentValues(); values.put(MediaStore.MediaColumns.DISPLAY_NAME, fileName); values.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/OWN's PDF Gallery");
            Uri collection = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? MediaStore.Files.getContentUri("external") : MediaStore.Files.getContentUri("external");
            Uri insertedUri = activity.getContentResolver().insert(collection, values);
            if (insertedUri != null) {
                OutputStream os = activity.getContentResolver().openOutputStream(insertedUri);
                if (os != null) { byte[] buffer = new byte[8192]; int bytesRead; while ((bytesRead = is.read(buffer)) != -1) os.write(buffer, 0, bytesRead); os.close(); }
            }
            is.close(); return insertedUri;
        } catch (Exception e) { e.printStackTrace(); return null; }
    }
}