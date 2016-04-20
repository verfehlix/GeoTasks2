package pc.com.geotasks;

import android.Manifest;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.common.SignInButton;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.places.GeoDataApi;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.location.places.ui.PlaceAutocomplete;

import org.w3c.dom.Text;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

import pc.com.geotasks.database.SQLHelper;
import pc.com.geotasks.model.Task;

public class AddNewTaskActivity extends AppCompatActivity implements GoogleApiClient.OnConnectionFailedListener, GoogleApiClient.ConnectionCallbacks {

    SQLHelper db;

    Toolbar toolbar;
    Button exitButton;
    Button saveButton;
    EditText editTextTaskName;
    EditText editTextTaskDescription;
    EditText editTextLocationAutocomplete;
    EditText datePickerEditText;
    EditText meterEditText;
    EditText timePickerEditText;
    EditText categoryEditText;
    TextView textViewLngLtd;
    TextView orTextView;
    Switch useCurrentLocationSwitch;
    SeekBar radiusSeekBar;

    Calendar cal = Calendar.getInstance();
    int day = cal.get(Calendar.DAY_OF_MONTH);
    int month = cal.get(Calendar.MONTH);
    int year = cal.get(Calendar.YEAR);
    int hour = cal.get(Calendar.HOUR);
    int minute = cal.get(Calendar.MINUTE);

    Place currentPlace;
    Location lastKnownLocation;

    GoogleApiClient googleApiClient;
    private static final int REQUEST_CODE_AUTOCOMPLETE = 1;
    public static final String TAG = TaskListFragment.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_new_task);

        //setup connection to database
        this.db = new SQLHelper(this.getApplicationContext());

        //init location Manager
        setUpGPSService();

        //clear place
        this.currentPlace = null;

        //setup toolbar
        toolbar = (Toolbar) findViewById(R.id.my_toolbar);
        setSupportActionBar(toolbar);
        //hide the title
        getSupportActionBar().setDisplayShowTitleEnabled(false);

        //get buttons
        exitButton = (Button) findViewById(R.id.exitButton);
        saveButton = (Button) findViewById(R.id.saveButton);

        //get inputs
        editTextTaskName = (EditText) findViewById(R.id.editTextTaskName);
        editTextTaskDescription = (EditText) findViewById(R.id.editTextTaskDescription);
        editTextLocationAutocomplete = (EditText) findViewById(R.id.editTextLocationAutocomplete);
        datePickerEditText = (EditText) findViewById(R.id.datePickerEditText);
        timePickerEditText = (EditText) findViewById(R.id.timePickerEditText);
        meterEditText = (EditText) findViewById(R.id.meterEditText);
        categoryEditText = (EditText) findViewById(R.id.categoryEditText);

        //get text views
        textViewLngLtd = (TextView) findViewById(R.id.textViewLngLtd);
        orTextView = (TextView) findViewById(R.id.orTextView);
        meterEditText = (EditText) findViewById(R.id.meterEditText);

        //get switch
        useCurrentLocationSwitch = (Switch) findViewById(R.id.useCurrentLocationSwitch);

        //get radius seekbar
        radiusSeekBar = (SeekBar) findViewById(R.id.radiusSeekBar);

        //add onchange listener to autocomplete edit text
        editTextLocationAutocomplete.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    findPlace(findViewById(android.R.id.content));
                }
            }
        });

        //add onchange listener to switch for use current location
        useCurrentLocationSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    editTextLocationAutocomplete.setVisibility(View.GONE);
                    orTextView.setVisibility(View.GONE);

                    //also set place for writing to the db to current position (use last known location)
                    if(lastKnownLocation != null){
                        double longtitude = lastKnownLocation.getLongitude();
                        double latitiude = lastKnownLocation.getLatitude();

                        textViewLngLtd.setText(longtitude + ", " + latitiude);
                    } else {
                        Toast.makeText(AddNewTaskActivity.this, "Currnent location could not be recieved!", Toast.LENGTH_SHORT).show();
                    }


                } else {
                    editTextLocationAutocomplete.setVisibility(View.VISIBLE);
                    orTextView.setVisibility(View.VISIBLE);
                }
            }
        });

        //add change listener to radius slider
        radiusSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                meterEditText.setText("" + progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        //add change listener to meter input
        meterEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() == 0) {
                    return;
                }

                int inputValue = Integer.parseInt(s.toString());

                if (inputValue > 5000) {
                    meterEditText.setText("" + 5000);
                    radiusSeekBar.setProgress(5000);
                } else {
                    radiusSeekBar.setProgress(inputValue);
                }

                meterEditText.setSelection(meterEditText.getText().length());
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        //add date / time picker click listeners
        datePickerEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if(hasFocus){
                    datePicker();
                }
            }
        });
        datePickerEditText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                datePicker();
            }
        });

        //add date / time picker click listeners
        timePickerEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if(hasFocus){
                    timePicker();
                }
            }
        });
        timePickerEditText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                timePicker();
            }
        });


        //init google maps api component
        // ATTENTION: This "addApi(AppIndex.API)"was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        googleApiClient = new GoogleApiClient
                .Builder(this)
                .addApi(Places.GEO_DATA_API)
                .addApi(Places.PLACE_DETECTION_API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(AppIndex.API).build();

    }

    @Override
    protected void onStart() {
        super.onStart();
        if (googleApiClient != null)
            googleApiClient.connect();
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "AddNewTask Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app URL is correct.
                Uri.parse("android-app://pc.com.geotasks/http/host/path")
        );
        AppIndex.AppIndexApi.start(googleApiClient, viewAction);
    }

    @Override
    protected void onStop() {
        if (googleApiClient != null && googleApiClient.isConnected()) {
            googleApiClient.disconnect();
        }
        super.onStop();
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        Action viewAction = Action.newAction(
                Action.TYPE_VIEW, // TODO: choose an action type.
                "AddNewTask Page", // TODO: Define a title for the content shown.
                // TODO: If you have web page content that matches this app activity's content,
                // make sure this auto-generated web page URL is correct.
                // Otherwise, set the URL to null.
                Uri.parse("http://host/path"),
                // TODO: Make sure this auto-generated app URL is correct.
                Uri.parse("android-app://pc.com.geotasks/http/host/path")
        );
        AppIndex.AppIndexApi.end(googleApiClient, viewAction);
    }

    @Override
    public void onBackPressed() {
        exitButtonPressed(findViewById(android.R.id.content));
    }

    public void exitButtonPressed(View view){
        //check if any data was entered
        if(anyDataEntered()){
            //prompt user if he really wants to exit
            exitPrompt();
        } else {
            //no data was entered, just exit this activity
            finish();
        }
    }

    public void saveButtonPressed(View view) throws ParseException {
        String taskName = editTextTaskName.getText().toString();
        String taskDescription = editTextTaskDescription.getText().toString();
        String locationName = this.currentPlace != null? this.currentPlace.getName().toString(): "";
        String locationAddress = this.currentPlace != null? this.currentPlace.getAddress().toString(): "";
        String tag = categoryEditText.getText().toString();
        double longitude = useCurrentLocationSwitch.isChecked() ? lastKnownLocation.getLongitude() : this.currentPlace.getLatLng().longitude;
        double latitude = useCurrentLocationSwitch.isChecked() ? lastKnownLocation.getLatitude() : this.currentPlace.getLatLng().latitude;
        int radius = radiusSeekBar.getProgress();
        String dateString = datePickerEditText.getText().toString() + " " + timePickerEditText.getText().toString() + ":00";
        Date dueDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse("1900-01-01 00:00:00");

        if(dateString.length() != 0){
            dueDate = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(dateString);
        }

        Task t = new Task(taskName, taskDescription, tag, locationName, locationAddress, longitude, latitude, radius, dueDate);
        db.addTask(t);

        finish();
    }

    private void datePicker() {
        DatePickerDialog.OnDateSetListener onDateSetListener = new DatePickerDialog.OnDateSetListener() {

            @Override
            public void onDateSet(DatePicker view, int year, int monthOfYear, int dayOfMonth) {
                String monthString = (""+monthOfYear).length() == 1 ? "0" + monthOfYear : monthOfYear+"";
                String dayString = (""+dayOfMonth).length() == 1 ? "0" + dayOfMonth : dayOfMonth+"";

                datePickerEditText.setText(year + "-" + monthString + "-" + dayString);

            }
        };

        DatePickerDialog dpDialog = new DatePickerDialog(this, onDateSetListener, year, month, day);

        dpDialog.show();
    }

    private void timePicker() {
        TimePickerDialog.OnTimeSetListener onTimeSetListener = new TimePickerDialog.OnTimeSetListener() {

            @Override
            public void onTimeSet(TimePicker view, int hourOfDay, int minute){
                String hourString = (""+hourOfDay).length() == 1 ? "0" + hourOfDay : hourOfDay+"";
                String minuteString = (""+minute).length() == 1 ? "0" + minute : minute+"";

                timePickerEditText.setText(hourString + ":" + minuteString);
            }

        };

        TimePickerDialog tpDialog = new TimePickerDialog(this, onTimeSetListener, hour, minute, true);

        tpDialog.show();
    }

    private void exitPrompt() {
        AlertDialog exitDialog = new AlertDialog.Builder(AddNewTaskActivity.this).create();
        exitDialog.setMessage("Do you really want to exit and discard this task?");
        exitDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Exit",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        finish();
                        dialog.dismiss();
                    }
                });
        exitDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Stay & Keep Editing",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        exitDialog.show();
    }

    private boolean anyDataEntered() {
        boolean anyDataEnterd = false;

        if(textEditFilled(editTextTaskName)) {
            anyDataEnterd = true;
        }

        if(textEditFilled(editTextTaskDescription)) {
            anyDataEnterd = true;
        }

        if(textEditFilled(editTextLocationAutocomplete)) {
            anyDataEnterd = true;
        }

        if(textEditFilled(datePickerEditText)) {
            anyDataEnterd = true;
        }

        if(textEditFilled(timePickerEditText)) {
            anyDataEnterd = true;
        }

        if (textEditFilled(categoryEditText)) {
            anyDataEnterd = true;
        }

        return anyDataEnterd;
    }

    private boolean textEditFilled(EditText editText){
        return editText.getText().toString().trim().length() > 0 ? true : false;
    }

    public void findPlace(View view) {
        try {

            Intent intent = new PlaceAutocomplete.IntentBuilder(PlaceAutocomplete.MODE_FULLSCREEN).build(this);
            startActivityForResult(intent, REQUEST_CODE_AUTOCOMPLETE);

        } catch (GooglePlayServicesRepairableException e) {
            GoogleApiAvailability.getInstance().getErrorDialog(this, e.getConnectionStatusCode(),0 /* requestCode */).show();
        } catch (GooglePlayServicesNotAvailableException e) {
            String message = "Google Play Services is not available: " + GoogleApiAvailability.getInstance().getErrorString(e.errorCode);

            Log.e(TAG, message);
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }

    // A place has been received; use requestCode to track the request.
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_AUTOCOMPLETE) {
            if (resultCode == RESULT_OK) {

                Place place = PlaceAutocomplete.getPlace(this, data);
                Log.i(TAG, "Place: " + place.getName());

                this.currentPlace = place;

                String placeText = place.getName() + ", " + place.getAddress();

                editTextLocationAutocomplete.setText(placeText);
                textViewLngLtd.setText(place.getLatLng().longitude + ", " + place.getLatLng().latitude);

            } else if (resultCode == PlaceAutocomplete.RESULT_ERROR) {
                Status status = PlaceAutocomplete.getStatus(this, data);
                // TODO: Handle the error.
                Log.i(TAG, status.getStatusMessage());

            } else if (resultCode == RESULT_CANCELED) {
                // The user canceled the operation.
            }
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.i("api",connectionResult.getErrorMessage());
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {

    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    public void setUpGPSService() {
        // Acquire a reference to the system Location Manager
        LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        //use last known location
        String locationProvider = LocationManager.GPS_PROVIDER;
        Location lastKnownLocation = locationManager.getLastKnownLocation(locationProvider);

        if(lastKnownLocation != null) {
            handleLocation(lastKnownLocation);
        }

        // Define a listener that responds to location updates
        LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                Log.d(TAG, "(addnewtaskactivity - Location changed");
                handleLocation(location);
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            public void onProviderEnabled(String provider) {
                Toast.makeText(getBaseContext(), "addnewtaskactivity - Gps turned on", Toast.LENGTH_LONG).show();
            }

            public void onProviderDisabled(String provider) {
                Toast.makeText(getBaseContext(), "addnewtaskactivity - Gps turned off", Toast.LENGTH_LONG).show();
            }
        };

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }

        // Register the listener with the Location Manager to receive location updates
        //
        // second parameters the minimum time interval between notifications
        // and the third is the minimum change in distance (meters) between notifications
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 10, locationListener);
    }

    /**
     * handles the current location and sends a notification if next to the location of the task
     * @param location
     */
    public void handleLocation(Location location){
        Toast toast = Toast.makeText(AddNewTaskActivity.this, "lat: " + location.getLatitude() + "\nlong" + location.getLongitude(), Toast.LENGTH_LONG);
        toast.show();

        lastKnownLocation = location;
    }
}
