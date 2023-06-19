package digital.slovensko.autogram.ui.cli;

import digital.slovensko.autogram.core.Autogram;
import digital.slovensko.autogram.core.ISigningJob;
import digital.slovensko.autogram.core.SigningKey;
import digital.slovensko.autogram.core.errors.AutogramException;
import digital.slovensko.autogram.drivers.TokenDriver;
import digital.slovensko.autogram.ui.UI;
import digital.slovensko.autogram.util.DSSUtils;
import eu.europa.esig.dss.token.DSSPrivateKeyEntry;

import java.io.File;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class CliUI implements UI {
    SigningKey activeKey;

    @Override
    public void startSigning(ISigningJob job, Autogram autogram) {
        System.out.println("Starting signing for " + job);
        if (activeKey == null) {
            autogram.pickSigningKeyAndThen(key -> {
                activeKey = key;
                autogram.sign(job, activeKey);
            });
        } else {
            autogram.sign(job, activeKey);
        }

    }

    @Override
    public void pickTokenDriverAndThen(List<TokenDriver> drivers, Consumer<TokenDriver> callback) {
        TokenDriver pickedDriver;
        if (drivers.size() == 1) {
            pickedDriver = drivers.get(0);
        } else {
            var i = new AtomicInteger(1);
            System.out.println("Vyberte ulozisko certifikatov");
            drivers.forEach(driver -> {
                System.out.print("[" + i + "] ");
                System.out.println(driver.getName());
                i.addAndGet(1);
            });
            pickedDriver = drivers.get(CliUtils.readInteger() - 1);
        }
        callback.accept(pickedDriver);
    }

    @Override
    public void requestPasswordAndThen(TokenDriver driver, Consumer<char[]> callback) {
        if (!driver.needsPassword()) {
            callback.accept(null);
            return;
        }
        System.out.println("Zadajte bezpecnostny kod k ulozisku certifikatov: ");
        callback.accept(CliUtils.readLine()); // TODO do not show pin
    }

    @Override
    public void pickKeyAndThen(List<DSSPrivateKeyEntry> keys, Consumer<DSSPrivateKeyEntry> callback) {
        if (keys.size() > 1) {
            System.out.println("Found multiple keys:");
            keys.forEach(key -> System.out.println(DSSUtils.buildTooltipLabel(key)));
        }

        System.out.println("Picking key: " + DSSUtils.buildTooltipLabel(keys.get(0)));
        callback.accept(keys.get(0));
    }

    @Override
    public void onWorkThreadDo(Runnable callback) {
        callback.run(); // no threads
    }

    @Override
    public void onUIThreadDo(Runnable callback) {
        callback.run(); // no threads
    }

    @Override
    public void onSigningSuccess(ISigningJob job) {
        System.out.println("Success for " + job);
    }

    @Override
    public void onSigningFailed(AutogramException e) {
        System.err.println(e);
    }

    @Override
    public void onDocumentSaved(File filename) {

    }

    @Override
    public void onPickSigningKeyFailed(AutogramException e) {
        System.err.println(e);
    }

    @Override
    public void onUpdateAvailable() {
        System.out.println("Newer version of Autogram exists. Visit ");
    }

    @Override
    public void onAboutInfo() {
        System.out.println("About autograms");
    }

    @Override
    public void onPDFAComplianceCheckFailed(ISigningJob job) {

    }
}
