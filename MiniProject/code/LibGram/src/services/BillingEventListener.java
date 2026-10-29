package services;

import model.Charge;
import model.Payment;

/** Receives billing events after the ledger has been updated. */
public interface BillingEventListener {
    void onPaymentReceived(Payment payment);
    void onFineWaived(Charge charge);
    BillingEventListener NO_OP = new BillingEventListener() {
        @Override public void onPaymentReceived(Payment payment) { }
        @Override public void onFineWaived(Charge charge) { }
    };
}
