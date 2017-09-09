package com.just4fan.bilibilikit.model;

import android.graphics.BitmapFactory;
import android.os.Handler;
import android.util.Log;

import com.just4fan.bilibilikit.resource.StaticResouce;
import com.just4fan.bilibilikit.adapter.vlviewadapter.VLViewItem;
import com.just4fan.bilibilikit.json.JSON;
import com.just4fan.bilibilikit.json.SyntaxException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by chuiq on 2017/9/7.
 */

public class VideoList {
    static final String DEBUG_TAG = "VideoList";
    static final int MODE_COVER = 0x0;
    static final int MODE_APPEND = 0x1;
    static final int TYPE_PARENT = 0x0;
    static final int TYPE_SUB = 0x1;
    private List<VLViewItem> vlViewItemList;
    VLViewItem parent;
    List<File> paths;
    Handler handler;
    int type;
    int sub_type;
    public VideoList(Handler handler) {
        this();
        this.handler = handler;
    }
    public VideoList() {
        paths = new ArrayList<>();
        vlViewItemList = new ArrayList<>();
    }

    public void addVideo(File dir) {
        paths.add(dir);
        init(VideoList.MODE_APPEND);
    }

    public List<VLViewItem> getVlViewItemList() {
        return vlViewItemList;
    }

    public static VideoList getVideoList(File dir, Handler handler) {
        VideoList videoList = new VideoList(handler);
        videoList.paths.add(dir);
        videoList.type = VideoList.TYPE_PARENT;
        videoList.init(VideoList.MODE_COVER);
        return videoList;
    }

    protected void init(int mode) {
        if (mode == VideoList.MODE_COVER)
            this.vlViewItemList.clear();
        for(File dir : paths) {
            File[] dirs = dir.listFiles();
            for (File video : dirs) {
                VLViewItem item;
                VideoList videoList;
                //Log.d(DEBUG_TAG, video.getName());
                File[] parts = video.listFiles();
                if (parts == null || parts.length == 0)
                    continue;
                int type = VLViewItem.TYPE_VIDEO;
                if (parts.length > 1)
                    type = VLViewItem.TYPE_VIDEOS;
                if (video.getName().startsWith("s_"))
                    type = VLViewItem.TYPE_DRAMA;
                switch (type) {
                    case VLViewItem.TYPE_VIDEO:
                        VLViewItem temp = getVLViewItem(parts[0], null, type);
                        if (temp != null)
                            this.vlViewItemList.add(temp);
                        break;
                    case VLViewItem.TYPE_VIDEOS:
                        item = new VLViewItem(VLViewItem.TYPE_VIDEOS);
                        videoList = new VideoList(handler);
                        videoList.paths.add(video);
                        Log.d(DEBUG_TAG, "VIDEOS" + video.getName());
                        videoList.type =VideoList.TYPE_SUB;
                        videoList.sub_type = VLViewItem.TYPE_VIDEOS;
                        videoList.parent = item;
                        videoList.init_sub(item, type);
                        item.setVideoList(videoList);
                        this.vlViewItemList.add(item);
                        break;
                    case VLViewItem.TYPE_DRAMA:
                        item = new VLViewItem(VLViewItem.TYPE_DRAMA);
                        videoList = new VideoList(handler);
                        videoList.paths.add(video);
                        Log.d(DEBUG_TAG, "DRAMA" + video.getName());
                        videoList.type =VideoList.TYPE_SUB;
                        videoList.sub_type = VLViewItem.TYPE_DRAMA;
                        videoList.parent = item;
                        videoList.init_sub(item, type);
                        item.setVideoList(videoList);
                        this.vlViewItemList.add(item);
                        break;
                    default:
                        break;
                }
            }
        }
    }


    protected void init_sub(VLViewItem parent, int type) {
        for(File dir : paths) {
            Log.d(DEBUG_TAG, "DIR:" + dir.getName());
            File[] parts = dir.listFiles();
            if (parts == null || parts.length == 0)
                return;
            for (File part : parts) {
                VLViewItem item = getVLViewItem(part, parent, type);
                if (item != null)
                    vlViewItemList.add(item);
            }
        }
    }

    public void Refresh() {
        Log.d(DEBUG_TAG, "Refresh Start," + type);
        if(type == VideoList.TYPE_PARENT)
            init(MODE_COVER);
        else if(type == VideoList.TYPE_SUB) {
            vlViewItemList.clear();
            init_sub(parent, sub_type);
        };
    }

    private static VideoList getVideoListFromSub(File video, VLViewItem parent, int type) {
        VideoList videoList = new VideoList();
        //videoList.dir = video;
        videoList.type =VideoList.TYPE_SUB;
        File[] parts = video.listFiles();
        if(parts == null || parts.length ==0)
            return null;
        for(File part : parts) {
            VLViewItem  item = videoList.getVLViewItem(part, parent, type);
            if(item != null)
                videoList.vlViewItemList.add(item);
        }
        return videoList;
    }

    public void getCover(final String cover, final VLViewItem item) {
        new Thread() {
            public void run() {
                try {
                    String s = cover;
                    s = s.replaceAll("\\\\", "");
                    URL url = new URL(s);
                    try {
                        InputStream inputStream = url.openStream();
                        File file = new File(StaticResouce.cachePath, s.hashCode() + "");
                        if(file.exists()) {
                            item.setCover_map(BitmapFactory.decodeFile(file.getAbsolutePath()));
                            handler.sendEmptyMessage(1);
                            return;
                        }
                        FileOutputStream fileOutputStream = new FileOutputStream(file);
                        int len;
                        byte[] bytes = new byte[512];
                        while((len = inputStream.read(bytes, 0, 512)) != -1) {
                            fileOutputStream.write(bytes, 0, len);
                        }
                        item.setCover_map(BitmapFactory.decodeFile(file.getAbsolutePath()));
                        handler.sendEmptyMessage(1);
                    }catch (IOException e) {
                        e.printStackTrace();
                    }
                }catch (MalformedURLException e) {
                    e.printStackTrace();
                }
                handler.sendEmptyMessage(0);
            }
        }.start();
    }

    private VLViewItem getVLViewItem(File part, VLViewItem parent, int type) {
        File entry = new File(part, "entry.json");
        String json = "";
        try {
            try {
                FileInputStream fileInputStream = new FileInputStream(entry);
                InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream, "UTF-8");
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                String temp;
                try {
                    while((temp = bufferedReader.readLine()) != null)
                        json += temp;
                    bufferedReader.close();
                    inputStreamReader.close();
                    fileInputStream.close();
                    JSON Json = new JSON(json);
                    try {
                        Json.parse();
                        Map<String, Object> map = Json.getRoot();
                        VLViewItem vlViewItem;
                        if(type == VLViewItem.TYPE_DRAMA) {
                            //Log.d(DEBUG_TAG, map.toString());
                            Map<String, Object> ep = (Map)map.get("ep");
                            long avid = (long)ep.get("av_id");
                            String cover0 = (String)map.get("cover");
                            String cover = (String)ep.get("cover");
                            String title = ep.get("index") + ":" + ep.get("index_title");
                            vlViewItem = new VLViewItem(avid,
                                    title,
                                    cover,
                                    (long)map.get("total_bytes"),
                                    (long)map.get("danmaku_count"),
                                    (String)map.get("type_tag"),
                                    (boolean)map.get("is_completed"));
                            vlViewItem.parts_dir = new File(part, (String)map.get("type_tag"));
                            Log.d(DEBUG_TAG, vlViewItem.parts_dir.getAbsolutePath());
                            getCover(cover0, parent);
                            getCover(cover, vlViewItem);
                            if(parent != null) {
                                parent.setCover(cover0);
                                parent.setTitle((String) map.get("title"));
                            }
                        }
                        else if(type == VLViewItem.TYPE_VIDEOS) {
                            Map<String, Object> page_data = (Map)map.get("page_data");
                            String cover = (String)map.get("cover");
                            String title = (String)page_data.get("part");
                            vlViewItem = new VLViewItem((long) map.get("avid"),
                                    title,
                                    cover,
                                    (long)map.get("total_bytes"),
                                    (long)map.get("danmaku_count"),
                                    (String)map.get("type_tag"),
                                    (boolean)map.get("is_completed"));
                            vlViewItem.parts_dir = new File(part, (String)map.get("type_tag"));
                            Log.d(DEBUG_TAG, vlViewItem.parts_dir.getAbsolutePath());
                            getCover(cover, vlViewItem);
                            getCover(cover, parent);
                            if(parent != null) {
                                parent.setCover((String) map.get("cover"));
                                parent.setTitle((String) map.get("title"));
                            }
                        }
                        else if(type == VLViewItem.TYPE_VIDEO){
                            String cover =  (String) map.get("cover");
                            vlViewItem = new VLViewItem((long) map.get("avid"),
                                    (String) map.get("title"),
                                    cover,
                                    (long) map.get("total_bytes"),
                                    (long) map.get("danmaku_count"),
                                    (String) map.get("type_tag"),
                                    (boolean) map.get("is_completed"));
                            vlViewItem.parts_dir = new File(part, (String)map.get("type_tag"));
                            Log.d(DEBUG_TAG, vlViewItem.parts_dir.getAbsolutePath());
                            getCover(cover, vlViewItem);
                        }
                        else
                            vlViewItem = null;
                        return vlViewItem;
                    }catch (SyntaxException e) {
                        e.printStackTrace();
                    }
                }catch (IOException e) {
                    e.printStackTrace();
                }
            }catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void setHandler(Handler handler) {
        this.handler = handler;
    }
}