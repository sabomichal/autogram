package digital.slovensko.autogram.ui.cli;

import digital.slovensko.autogram.core.*;
import digital.slovensko.autogram.core.errors.AutogramException;
import digital.slovensko.autogram.drivers.TokenDriver;
import digital.slovensko.autogram.ui.UI;
import eu.europa.esig.dss.token.DSSPrivateKeyEntry;

import java.util.List;

public class CliUI implements UI {
    public void start(String[] args) {
        System.out.println("Starting CLI with args " + args.toString());
    }

    @Override
    public void pickKeyAndThen(List<DSSPrivateKeyEntry> keys, PrivateKeyLambda callback) {
        System.out.println("Found " + keys.size() + " keys, picking first!");
        callback.call(keys.get(0));
    }

    @Override
    public void pickTokenDriverAndThen(List<TokenDriver> drivers, TokenDriverLambda callback) {
        System.out.println("Found " + drivers.size() + " drivers, picking first!");
        callback.call(drivers.get(0));
    }

    @Override
    public void showSigningDialog(SigningJob job) {
        System.out.println("Dialog for signing " + job.getDocument().toString() + " started!");
        System.out.println("Assuming user clicked to sign it!");
    }

    @Override
    public void hideSigningDialog(SigningJob job) {
        System.out.println("Dialog for signing " + job.getDocument().toString() + " closed!");
    }

    @Override
    public void refreshSigningKey() {
        System.out.println("Showing new signing key on all dialogs!");
    }

    @Override
    public void showError(AutogramException e) {
        System.out.println("Error " + e.toString() + " closed!");
    }

    @Override
    public void showPasswordDialogAndThen(TokenDriver driver, PasswordLambda callback) {
        callback.call(null); // TODO
    }

    @Override
    public void showPickFileDialog() {

    }
}
