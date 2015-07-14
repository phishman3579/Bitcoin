package com.jwetherell.bitcoin.data_model;

public class Wallet {

    private final String    name;

    private long            balance     = 0;

    public Wallet(String name) {
        this.name = name;
    }

    public void addCoin(Coin coin) {
        balance += coin.value;
        System.out.println("Added coin={"+coin.toString()+"} to "+name+"'s wallet. "+toString());
    }

    public Coin removeCoin(int value) {
        balance -= value;
        String msg = "Removed "+value+" from "+name+"'s wallet. "+toString();
        System.out.println(msg);
        return (new Coin(msg, value));
    }

    public long getBalance() {
        return balance;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("name=").append(name).append(" ");
        builder.append("balance=").append(balance);
        return builder.toString();
    }
}
