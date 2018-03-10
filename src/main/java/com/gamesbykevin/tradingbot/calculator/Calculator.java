package com.gamesbykevin.tradingbot.calculator;

import com.gamesbykevin.tradingbot.util.GSon;

import java.util.ArrayList;
import java.util.List;

import static com.gamesbykevin.tradingbot.Main.ENDPOINT;
import static com.gamesbykevin.tradingbot.calculator.EMA.calculateEMA;
import static com.gamesbykevin.tradingbot.calculator.OBV.calculateOBV;
import static com.gamesbykevin.tradingbot.calculator.RSI.calculateRsi;
import static com.gamesbykevin.tradingbot.util.JSon.getJsonResponse;

public class Calculator {

    //keep a list of our periods
    private List<Period> history;

    //keep a historical list of the rsi so we can check for divergence
    private List<Double> rsi;

    //keep a historical list of the volume so we can check for divergence
    private List<Double> volume;

    //list of ema values for our long period
    private List<Double> emaLong;

    //list of ema values for our short period
    private List<Double> emaShort;

    /**
     * How many periods to calculate rsi
     */
    public static int PERIODS_RSI;

    /**
     * How many periods to calculate long ema
     */
    public static int PERIODS_EMA_LONG;

    /**
     * How many periods to calculate short ema
     */
    public static int PERIODS_EMA_SHORT;

    /**
     * How many periods to calculate the on balance volume
     */
    public static int PERIODS_OBV;

    /**
     * How many periods do we check to confirm a crossover?
     */
    public static int EMA_CROSSOVER;

    //endpoint to get the history
    public static final String ENDPOINT_HISTORIC = ENDPOINT + "/products/%s/candles?granularity=%s";

    //endpoint to get the history
    public static final String ENDPOINT_TICKER = ENDPOINT + "/products/%s/ticker";

    public enum Trend {
        Upward, Downward, None
    }

    //what is the current trend?
    private Trend trend = Trend.None;

    //total number of breaks
    private int breaks = 0;

    /**
     * How long is each period?
     */
    public static int PERIOD_DURATION = 0;

    public enum Duration {

        OneMinute(60),
        FiveMinutes(300),
        FifteenMinutes(900),
        OneHour(3600),
        SixHours(21600),
        TwentyFourHours(86400);

        public final long duration;

        Duration(long duration) {
            this.duration = duration;
        }
    }

    public Calculator() {
        this.history = new ArrayList<>();
        this.rsi = new ArrayList<>();
        this.volume = new ArrayList<>();
        this.emaShort = new ArrayList<>();
        this.emaLong = new ArrayList<>();
    }

    public synchronized boolean update(Duration key, String productId) {

        //were we successful
        boolean result = false;

        try {

            //make our rest call and get the json response
            final String json = getJsonResponse(String.format(ENDPOINT_HISTORIC, productId, key.duration));

            //convert json text to multi array
            double[][] data = GSon.getGson().fromJson(json, double[][].class);

            //make sure we have data before we update
            if (data != null && data.length > 0) {

                //clear the current list
                this.history.clear();

                //parse each period from the data
                for (int row = data.length - 1; row >= 0; row--) {

                    //create and populate our period
                    Period period = new Period();
                    period.time = (long) data[row][0];
                    period.low = data[row][1];
                    period.high = data[row][2];
                    period.open = data[row][3];
                    period.close = data[row][4];
                    period.volume = data[row][5];

                    //add to array list
                    this.history.add(period);
                }

                //calculate the rsi for all our specified periods now that we have new data
                calculateRsi(history, rsi);

                //calculate the on balance volume for all our specified periods now that we have new data
                calculateOBV(history, volume);

                //calculate the ema for the long period now that we have new data
                calculateEMA(history, emaLong, PERIODS_EMA_LONG);

                //calculate the ema for the short period now that we have new data
                calculateEMA(history, emaShort, PERIODS_EMA_SHORT);

                //we are successful
                result = true;

            } else {
                result = false;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        //return our result
        return result;
    }

    private void setTrend(final Trend trend) {
        this.trend = trend;
    }

    private void setBreaks(final int breaks) {
        this.breaks = breaks;
    }

    public Trend getTrend() {
        return this.trend;
    }

    public int getBreaks() {
        return this.breaks;
    }

    public synchronized void calculateTrend(final double currentPrice) {

        //reset values
        setTrend(Trend.None);
        setBreaks(0);

        //if not large enough skip, this shouldn't happen
        if (history.size() < PERIODS_RSI || history.isEmpty())
            return;

        //we want to start here
        Period begin = history.get(history.size() - PERIODS_RSI);

        //are we checking an upward trend?
        if (begin.close < currentPrice) {
            setTrend(Trend.Upward);
        } else if (begin.close > currentPrice) {
            setTrend(Trend.Downward);
        } else {
            //no difference
            return;
        }

        //our coordinates to calculate slope
        final double x1 = 0, y1, x2 = PERIODS_RSI, y2;

        //are we detecting and upward or downward trend?
        switch (getTrend()) {
            case Upward:
                y1 = begin.low;
                y2 = currentPrice;
                break;

            case Downward:
                y1 = begin.high;
                y2 = currentPrice;
                break;

            case None:
            default:
                return;
        }

        //the value of y when x = 0
        final double yIntercept = y1;

        //calculate slope
        final double slope = (y2 - y1) / (x2 - x1);

        //check and see if every period is above the slope indicating an upward trend
        for (int i = history.size() - PERIODS_RSI; i < history.size(); i++) {

            //get the current period
            Period current = history.get(i);

            //the current x-coordinate
            final double x = i - (history.size() - PERIODS_RSI);

            //calculate the y-coordinate
            final double y = (slope * x) + yIntercept;

            //are we checking for an upward trend
            switch (getTrend()) {

                case Upward:

                    //if the current low is below the calculated y-coordinate slope we have a break
                    if (current.low < y)
                        setBreaks(getBreaks() + 1);
                    break;

                case Downward:

                    //if the current high is above the calculated y-coordinate slope we have a break
                    if (current.high > y)
                        setBreaks(getBreaks() + 1);
                    break;
            }
        }
    }

    public synchronized boolean hasDivergence(final boolean uptrend, final double currentPrice, final double currentRsi, final double currentVolume) {

        //flag we will use to track if the price is following the desired trend
        boolean betterPrice = true;

        //check all recent periods
        for (int i = history.size() - PERIODS_OBV; i < history.size(); i++) {

            //get the current period
            Period period = history.get(i);

            if (uptrend) {

                //if we are checking for an uptrend we don't want any "high" price higher than our current price
                if (period.high > currentPrice) {
                    betterPrice = false;
                    break;
                }

            } else {

                //if we are checking for a downtrend we don't want any "low" price lower than our current price
                if (period.low < currentPrice) {
                    betterPrice = false;
                    break;
                }
            }
        }

        //if we don't have a better price, we don't have a divergence
        if (!betterPrice)
            return false;

        //is the current volume the best whether it's an up or down trend?
        final boolean betterVolume = isCurrentBest(volume, currentVolume, uptrend);

        //if the price is better but the volume isn't that means we have a divergence
        return (betterPrice && !betterVolume);

        /*
        //is the current RSI the best whether it's an up or down trend?
        final boolean betterRsi = isCurrentBest(rsi, currentRsi, uptrend);

        //if the price is better but the RSI isn't that means we have a divergence
        return (betterPrice && !betterRsi);
        */
    }

    private boolean isCurrentBest(List<Double> list, double currentValue, boolean uptrend) {

        //look at all our volume periods
        for (int i = 0; i < list.size(); i++) {

            if (uptrend) {

                //if checking uptrend we don't want any values higher
                if (list.get(i) > currentValue)
                    return false;

            } else {

                //if checking downtrend we don't want any values lower
                if (list.get(i) < currentValue)
                    return false;
            }
        }

        //yep the current value is the best
        return true;
    }

    public double getObvCurrent() {
        return volume.get(volume.size() - 1);
    }

    public double getRsiCurrent(double currentPrice) {
        return calculateRsi(history, history.size() - PERIODS_RSI, history.size(), true, currentPrice);
    }

    public boolean hasEmaCrossover(boolean bullish) {

        //where do we start checking
        int start = EMA_CROSSOVER + 1;

        //if we are checking bullish the long is greater then the short is greater
        if (bullish) {

            //to start we want the long to be greater than the short
            if (emaLong.get(emaLong.size() - start) > emaShort.get(emaShort.size() - start)) {

                //now we want the short to be greater than the long
                for (int index = start - 1; index > 0; index--) {

                    //if the short is less, we can't confirm a crossover
                    if (emaShort.get(emaShort.size() - index) < emaLong.get(emaLong.size() - index))
                        return false;
                }

                //lets also make sure the ema short line is constantly increasing
                for (int index = emaShort.size() - 1; index >= emaShort.size() - EMA_CROSSOVER + 1; index--) {

                    //if the previous ema value is greater return false
                    if (emaShort.get(index) < emaShort.get(index - 1))
                        return false;
                }

                //lets also make sure the ema long line is constantly decreasing
                for (int index = emaLong.size() - 1; index >= emaLong.size() - EMA_CROSSOVER + 1; index--) {

                    //if the previous ema value is less return false
                    if (emaLong.get(index) > emaLong.get(index - 1))
                        return false;
                }

                //we found everything as expected
                return true;
            }

        } else {

            //to start we want the short to be greater than the long
            if (emaLong.get(emaLong.size() - start) < emaShort.get(emaShort.size() - start)) {

                //now we want the long to be greater than the short
                for (int index = start - 1; index > 0; index--) {

                    //if the long is less, we can't confirm a crossover
                    if (emaShort.get(emaShort.size() - index) > emaLong.get(emaLong.size() - index))
                        return false;
                }

                //lets also make sure the ema short line is constantly decreasing
                for (int index = emaShort.size() - 1; index >= emaShort.size() - EMA_CROSSOVER + 1; index--) {

                    //if the previous ema value is less return false
                    if (emaShort.get(index) > emaShort.get(index - 1))
                        return false;
                }

                //lets also make sure the ema long line is constantly increasing
                for (int index = emaLong.size() - 1; index >= emaLong.size() - EMA_CROSSOVER + 1; index--) {

                    //if the previous ema value is greater return false
                    if (emaLong.get(index) < emaLong.get(index - 1))
                        return false;
                }

                //we found everything as expected
                return true;
            }
        }

        //no crossover detected
        return false;
    }

    public List<Double> getEmaShort() {
        return this.emaShort;
    }

    public List<Double> getEmaLong() {
        return this.emaLong;
    }
}