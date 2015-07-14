package com.jwetherell.bitcoin;

import java.util.Queue;

import com.jwetherell.bitcoin.data_model.Data;

public interface Sender {

    public Queue<Data> getQueue();

}
