package uiza.v3.canslide;

/**
 * Created by www.muathu@gmail.com on 12/24/2017.
 */

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;

import uiza.R;
import vn.loitp.core.base.BaseFragment;
import vn.loitp.restapi.uiza.model.v2.listallentity.Item;
import vn.loitp.restapi.uiza.model.v3.videoondeman.retrieveanentity.ResultRetrieveAnEntity;
import vn.loitp.uizavideo.view.rl.videoinfo.ItemAdapterV2;
import vn.loitp.uizavideov3.view.rl.videoinfo.UizaIMAVideoInfoV3;

public class FrmVideoBottomV3 extends BaseFragment implements ItemAdapterV2.Callback {
    private final String TAG = getClass().getSimpleName();
    private UizaIMAVideoInfoV3 uizaIMAVideoInfo;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        uizaIMAVideoInfo = (UizaIMAVideoInfoV3) view.findViewById(R.id.uiza_video_info);
        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    protected int setLayoutResourceId() {
        return R.layout.v3_frm_bottom;
    }

    public void setup(ResultRetrieveAnEntity resultRetrieveAnEntity) {
        uizaIMAVideoInfo.setup(resultRetrieveAnEntity);
    }

    @Override
    public void onClickItemBottom(Item item, int position) {

    }

    @Override
    public void onLoadMore() {
    }

    public void init(ItemAdapterV2.Callback callback) {
        uizaIMAVideoInfo.init(callback);
    }

    public void clearAllViews() {
        if (uizaIMAVideoInfo != null) {
            uizaIMAVideoInfo.clearAllViews();
        }
    }
}
