package com.gamesbykevin.tradingbot.calculator.strategy;

import com.gamesbykevin.tradingbot.agent.Agent;
import com.gamesbykevin.tradingbot.calculator.Period;
import com.gamesbykevin.tradingbot.transaction.TransactionHelper.ReasonSell;
import com.gamesbykevin.tradingbot.transaction.TransactionHelper.ReasonBuy;

import java.util.ArrayList;
import java.util.List;

import static com.gamesbykevin.tradingbot.calculator.CalculatorHelper.hasCrossover;

public class MACD extends Strategy {

    //macdLine values
    private List<Double> macdLine;

    //list of ema values from the macd line
    private List<Double> signalLine;

    //our histogram (macdLine - signalLine)
    private List<Double> histogram;

    //our ema object
    private EMA emaObj;

    /**
     * How many periods do we calculate ema from macd line
     */
    public static int PERIODS_MACD;

    public MACD() {
        this(PERIODS_MACD);
    }

    public MACD(int periods) {

        //call parent
        super(periods);

        this.macdLine = new ArrayList<>();
        this.signalLine = new ArrayList<>();
        this.emaObj = new EMA();
        this.histogram = new ArrayList<>();
    }

    public List<Double> getMacdLine() {
        return this.macdLine;
    }

    public List<Double> getSignalLine() {
        return this.signalLine;
    }

    public List<Double> getHistogram() {
        return this.histogram;
    }

    @Override
    public void checkBuySignal(Agent agent, List<Period> history, double currentPrice) {

        if (hasCrossover(true, getMacdLine(), getSignalLine()))
            agent.setReasonBuy(ReasonBuy.Reason_2);

        //display our data
        displayData(agent, agent.getReasonBuy() != null);
    }

    @Override
    public void checkSellSignal(Agent agent, List<Period> history, double currentPrice) {

        //if we have a bearish crossover, we expect price to go down
        if (hasCrossover(false, getMacdLine(), getSignalLine()))
            agent.setReasonSell(ReasonSell.Reason_2);

        //display our data
        displayData(agent, agent.getReasonSell() != null);
    }

    @Override
    public void displayData(Agent agent, boolean write) {

        //display the recent MACD values which we use as a signal
        display(agent, "MACD Line: ", getMacdLine(), getPeriods(), write);
        display(agent, "Signal Line: ", getSignalLine(), getPeriods(), write);

        //display values
        this.emaObj.displayData(agent, write);
    }

    @Override
    public void calculate(List<Period> history) {

        //calculate our short and long ema values first
        this.emaObj.calculate(history);

        //now we can calculate our macd line
        calculateMacdLine(this.emaObj.getEmaShort(), this.emaObj.getEmaLong(), getMacdLine());

        //then we can calculate our signal line
        calculateEmaList(getSignalLine(), getMacdLine(), getPeriods());

        //last we can calculate the histogram
        calculateHistogram(getMacdLine(), getSignalLine(), getHistogram());
    }

    protected static void calculateMacdLine(List<Double> emaShort, List<Double> emaLong, List<Double> macdLine) {

        //clear the list
        macdLine.clear();

        //we need to start at the right index
        int difference = emaShort.size() - emaLong.size();

        //calculate for every value possible
        for (int i = 0; i < emaLong.size(); i++) {

            //the macd line is the 12 day ema - 26 day ema
            macdLine.add(emaShort.get(difference + i) - emaLong.get(i));
        }
    }

    /**
     * Calculate ema and populate the provided emaList
     * @param emaList Our result ema list that needs calculations
     * @param valueList The list of values we will use to do the calculations
     * @param periods The range of periods to make each calculation
     */
    protected static void calculateEmaList(List<Double> emaList, List<Double> valueList, int periods) {

        //clear list
        emaList.clear();

        //we add the sum to get the sma (simple moving average)
        double sum = 0;

        //calculate sma first
        for (int i = 0; i < periods; i++) {
            sum += valueList.get(i);
        }

        //we now have the sma as a start
        final double sma = sum / (float)periods;

        //here is our multiplier
        final double multiplier = ((float)2 / ((float)periods + 1));

        //calculate our first ema
        final double ema = ((valueList.get(periods - 1) - sma) * multiplier) + sma;

        //add the ema value to our list
        emaList.add(ema);

        //now let's calculate the remaining periods for ema
        for (int i = periods; i < valueList.size(); i++) {

            //get our previous ema
            final double previousEma = emaList.get(emaList.size() - 1);

            //get our close value
            final double close = valueList.get(i);

            //calculate our new ema
            final double newEma = ((close - previousEma) * multiplier) + previousEma;

            //add our new ema value to the list
            emaList.add(newEma);
        }
    }

    protected static void calculateHistogram(List<Double> macdLine, List<Double> signalLine, List<Double> histogram) {

        //clear list
        histogram.clear();

        //determine how long back we can calculate the histogram
        int length = (macdLine.size() > signalLine.size()) ? signalLine.size() - 1 : macdLine.size() - 1;

        //loop through and calculate the histogram
        for (int i = length; i > 0; i--) {
            histogram.add(macdLine.get(i) - signalLine.get(i));
        }
    }
}