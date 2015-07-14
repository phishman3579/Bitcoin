package com.jwetherell.bitcoin;

import java.util.Queue;

import com.jwetherell.bitcoin.data_model.Data;

public interface Receiver {

    public Queue<Data> getQueue();

    public String getHost();

    public int getPort();
}
