package com.robertnorthard.dtbs.mobile.android.dtbsandroidclient.dtbsandroidclient.service.communication.event;

import android.util.Log;

import com.google.android.gms.maps.model.LatLng;
import com.robertnorthard.dtbs.mobile.android.dtbsandroidclient.dtbsandroidclient.cache.AllTaxis;
import com.robertnorthard.dtbs.mobile.android.dtbsandroidclient.dtbsandroidclient.model.Location;
import com.robertnorthard.dtbs.mobile.android.dtbsandroidclient.dtbsandroidclient.model.Taxi;
import com.robertnorthard.dtbs.mobile.android.dtbsandroidclient.dtbsandroidclient.service.GeocodeService;
import com.robertnorthard.dtbs.mobile.android.dtbsandroidclient.dtbsandroidclient.service.TaxiService;
import com.robertnorthard.dtbs.server.common.dto.TaxiLocationEventDto;

import org.json.JSONException;

import java.io.IOException;
import java.util.Observable;
import java.util.Observer;

/**
 * Implements Taxi Events Facade.
 *
 * @author robertnorthard
 */
public class TaxiEventsService implements Observer {

    // TAG used for logging.
    private static final String TAG = TaxiEventsService.class.getName();

    // singleton instance.
    private static TaxiEventsService taxiEventsService;

    private AllTaxis allTaxis;
    private GeocodeService geocodeService;
    private TaxiService taxiService;
    private Location currentLocation;

    /**
     * Default constructor for class TaxiEventService
     */
    private TaxiEventsService(){
        this.allTaxis = AllTaxis.getInstance();
        this.geocodeService = new GeocodeService();
        this.taxiService = new TaxiService();

        MessageEventBus.getInstance().addObserver(this);
        MessageEventBus.getInstance().open();
    }

    /**
     * Returns a single instance if exists else instantiate new.
     * @return singleton instance of a taxi events service.
     */
    public static TaxiEventsService getInstance(){
        if(TaxiEventsService.taxiEventsService == null){
            synchronized (TaxiEventsService.class){
                TaxiEventsService.taxiEventsService = new TaxiEventsService();
            }
        }
        return TaxiEventsService.taxiEventsService;
    }

    /**
     * Update location of user.
     *
     * @param location last known location of user.
     */
    public void updateLocation(Location location){
        this.currentLocation = location;
    }

    /**
     * Recalculate average waiting time.
     *
     * @param event new taxi location event.
     */
    private void updateTaxiWaitTime(final TaxiLocationEventDto event){

        final Taxi taxi = this.allTaxis.findItem(event.getTaxiId());

        if(taxi != null && this.currentLocation != null) {

            // convert to appropriate location format.
            final LatLng startLocation = new LatLng(this.currentLocation.getLatitude(), this.currentLocation.getLongitude());
            final LatLng endLocation = new LatLng(event.getLocation().getLatitude(), event.getLocation().getLongitude());

            new Thread(new Runnable() {
                @Override
                public void run() {
                    int taxiWaitTime;

                    // calculate estimated travel time between user and taxi.
                    try {
                        taxiWaitTime = geocodeService.estimateTravelTime(startLocation, endLocation);
                    } catch (IOException e) {
                        Log.e(TAG, e.getMessage());
                        return;
                    } catch (JSONException e) {
                        Log.e(TAG, e.getMessage());
                        return;
                    }

                    // set new wait time
                    taxi.setTimeFromPassenger(taxiWaitTime);

                    // update taxi location
                    taxi.setLocation(new Location(event.getLocation().getLatitude(), event.getLocation().getLongitude()));

                    // add taxi
                    allTaxis.addItem(taxi);
                }
            }).start();

        }
    }

    /**
     * Invoked when subject state changes.
     *
     * @param observable callback to subject.
     * @param data event data.
     */
    @Override
    public void update(Observable observable, Object data) {
        if(data instanceof TaxiLocationEventDto){
            TaxiLocationEventDto event = (TaxiLocationEventDto)data;
            this.updateTaxiWaitTime(event);
        }
    }
}