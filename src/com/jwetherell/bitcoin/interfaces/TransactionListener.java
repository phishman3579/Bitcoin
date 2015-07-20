package com.jwetherell.bitcoin.interfaces;

import com.jwetherell.bitcoin.common.Constants;
import com.jwetherell.bitcoin.data_model.Transaction;

public interface TransactionListener {

    public void onTransaction(String uid, Transaction transaction, Constants.Status status);

}
