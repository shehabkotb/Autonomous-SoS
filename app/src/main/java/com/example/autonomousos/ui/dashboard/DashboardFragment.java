package com.example.autonomousos.ui.dashboard;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PointF;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.example.autonomousos.BaseViewModel;
import com.example.autonomousos.R;
import com.example.autonomousos.model.VideoModel;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.Point;
import com.mapbox.mapboxsdk.Mapbox;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.maps.MapView;
import com.mapbox.mapboxsdk.maps.MapboxMap;
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback;
import com.mapbox.mapboxsdk.maps.Style;
import com.mapbox.mapboxsdk.style.layers.CircleLayer;
import com.mapbox.mapboxsdk.style.layers.SymbolLayer;
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static com.mapbox.mapboxsdk.style.expressions.Expression.interpolate;
import static com.mapbox.mapboxsdk.style.expressions.Expression.linear;
import static com.mapbox.mapboxsdk.style.expressions.Expression.stop;
import static com.mapbox.mapboxsdk.style.expressions.Expression.zoom;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.circleColor;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.circleOpacity;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.circleRadius;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconAllowOverlap;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconIgnorePlacement;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconImage;
import static com.mapbox.mapboxsdk.style.layers.PropertyFactory.iconSize;

public class DashboardFragment extends Fragment implements MapboxMap.OnMapClickListener, OnMapReadyCallback {

    private static final String SOURCE_ID = "SOURCE_ID";
    private static final String ICON_ID = "ICON_ID";
    private static final String VIDEO_LAYER = "VIDEO_LAYER";

    private static final float BASE_CIRCLE_INITIAL_RADIUS = 6f;
    private static final float RADIUS_WHEN_CIRCLES_MATCH_ICON_RADIUS = 13f;
    private static final float ZOOM_LEVEL_FOR_START_OF_BASE_CIRCLE_EXPANSION = 11f;
    private static final float ZOOM_LEVEL_FOR_SWITCH_FROM_CIRCLE_TO_ICON = 13f;
    private static final float FINAL_OPACITY_OF_SHADING_CIRCLE = .5f;
    private static final String BASE_CIRCLE_COLOR = "#3BC802";
    private static final String SHADING_CIRCLE_COLOR = "#858585";
    private static final String BASE_CIRCLE_LAYER_ID = "BASE_CIRCLE_LAYER_ID";
    private static final String SHADOW_CIRCLE_LAYER_ID = "SHADOW_CIRCLE_LAYER_ID";


    private MapView mapView;
    private BaseViewModel mBaseViewModel;
    private MapboxMap mMapboxMap;
    private FeatureCollection mFeatureCollection;
    private GeoJsonSource mSource;

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        // called before inflating or creating the view
        Mapbox.getInstance(getContext(), getString(R.string.mapbox_access_token));

        View root = inflater.inflate(R.layout.fragment_dashboard, container, false);

        mSource = new GeoJsonSource(SOURCE_ID);
        mapView = (MapView) root.findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);

        mapView.getMapAsync(this);

        mBaseViewModel = new ViewModelProvider(this).get(BaseViewModel.class);
        mBaseViewModel.init();
        mBaseViewModel.getVideoEntries().observe(getViewLifecycleOwner(), new Observer<ArrayList<VideoModel>>() {
            @Override
            public void onChanged(ArrayList<VideoModel> videoModels) {
                Log.d("firebase", "onChanged: videos entries changed");

                List<Feature> featuresList = new ArrayList<>();
                for (VideoModel video : videoModels) {
                    Feature videoGeoJson = Feature.fromGeometry(Point.fromLngLat(video.getLocation().getLongitude(), video.getLocation().getLatitude()));
                    videoGeoJson.addStringProperty("URL", video.getLink());
                    featuresList.add(videoGeoJson);
                }

                mFeatureCollection = FeatureCollection.fromFeatures(featuresList);

                if (mFeatureCollection != null)
                    mSource.setGeoJson(mFeatureCollection);
            }
        });

        return root;
    }

    @Override
    public void onMapReady(@NonNull MapboxMap mapboxMap) {

        mMapboxMap = mapboxMap;
        mapboxMap.setStyle(new Style.Builder().fromUri(Style.LIGHT)

                // Add the SymbolLayer icon image to the map style
                .withImage(ICON_ID, BitmapFactory.decodeResource(
                        getActivity().getResources(), R.drawable.ic_map_warning))


                // Adding a GeoJson source for the SymbolLayer icons.
                .withSource(mSource)

                ,new Style.OnStyleLoaded() {
            @Override
            public void onStyleLoaded(@NonNull Style style) {
                // Map is set up and the style has loaded. Now you can add additional data or make other map adjustments.
                mapboxMap.addOnMapClickListener(DashboardFragment.this);

                CircleLayer baseCircleLayer = new CircleLayer(BASE_CIRCLE_LAYER_ID, SOURCE_ID).withProperties(
                        circleColor(Color.parseColor(BASE_CIRCLE_COLOR)),
                        circleRadius(
                                interpolate(
                                        linear(), zoom(),
                                        stop(ZOOM_LEVEL_FOR_START_OF_BASE_CIRCLE_EXPANSION, BASE_CIRCLE_INITIAL_RADIUS),
                                        stop(ZOOM_LEVEL_FOR_SWITCH_FROM_CIRCLE_TO_ICON, RADIUS_WHEN_CIRCLES_MATCH_ICON_RADIUS)
                                )
                        )
                );
                style.addLayer(baseCircleLayer);

                // Add a "shading" CircleLayer, whose circles' radii will match the radius of the SymbolLayer
                // circular icon
                CircleLayer shadowTransitionCircleLayer = new CircleLayer(SHADOW_CIRCLE_LAYER_ID, SOURCE_ID)
                        .withProperties(
                                circleColor(Color.parseColor(SHADING_CIRCLE_COLOR)),
                                circleRadius(RADIUS_WHEN_CIRCLES_MATCH_ICON_RADIUS),
                                circleOpacity(
                                        interpolate(
                                                linear(), zoom(),
                                                stop(ZOOM_LEVEL_FOR_START_OF_BASE_CIRCLE_EXPANSION - .5, 0),
                                                stop(ZOOM_LEVEL_FOR_START_OF_BASE_CIRCLE_EXPANSION, FINAL_OPACITY_OF_SHADING_CIRCLE)
                                        )
                                )
                        );
                style.addLayerBelow(shadowTransitionCircleLayer, BASE_CIRCLE_LAYER_ID);

                // Add the SymbolLayer
                SymbolLayer symbolIconLayer = new SymbolLayer(VIDEO_LAYER, SOURCE_ID);
                symbolIconLayer.withProperties(
                        iconImage(ICON_ID),
                        iconSize(1.0f),
                        iconIgnorePlacement(true),
                        iconAllowOverlap(true)
                );

                symbolIconLayer.setMinZoom(ZOOM_LEVEL_FOR_SWITCH_FROM_CIRCLE_TO_ICON);
                style.addLayer(symbolIconLayer);

            }
        });
    }


    private boolean handleVideoClick(PointF screenPoint) {
        List<Feature> features = mMapboxMap.queryRenderedFeatures(screenPoint, VIDEO_LAYER);
        if (!features.isEmpty()) {
            String videoURL = features.get(0).getStringProperty("URL");
            Log.d("map", "url of video click on" + videoURL);

            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(videoURL), "video/mp4");
            startActivity(intent);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean onMapClick(@NonNull LatLng point) {
        return handleVideoClick(mMapboxMap.getProjection().toScreenLocation(point));
    }


    @Override
    public void onStart() {
        super.onStart();
        mapView.onStart();
    }

    @Override
    public void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mapView.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
        mapView.onStop();
    }

    @Override
    public void onSaveInstanceState(@NotNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mMapboxMap != null) {
            mMapboxMap.removeOnMapClickListener(this);
        }
        mapView.onDestroy();
    }


}