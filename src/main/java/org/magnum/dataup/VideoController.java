/*
 * My implementation of the Controller - mb
 */

package org.magnum.dataup;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.magnum.dataup.model.Video;
import org.magnum.dataup.model.VideoStatus;
import org.magnum.dataup.model.VideoStatus.VideoState;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestPart;


@RestController
public class VideoController {

	public static final String DATA_PARAMETER = "data";
	public static final String ID_PARAMETER = "id";
	public static final String VIDEO_SVC_PATH = "/video";
	public static final String VIDEO_DATA_PATH = VIDEO_SVC_PATH + "/{id}/data";
	
	/*
	 * Local "DB" of the videos uploaded. 
	 */
	private Map<Long, Video> videoList;
	
	private long id;
	private Object lock;
	private VideoFileManager fileManager;

	
	public VideoController() {
		videoList = new HashMap<Long, Video>();
		id = 0;
		lock = new Object();
		
		try {
			fileManager = VideoFileManager.get();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	
 	private String getUrlBaseForLocalServer() {
        HttpServletRequest request = 
            ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
        String base = 
           "http://"+request.getServerName() 
           + ((request.getServerPort() != 80) ? ":"+request.getServerPort() : "");
        return base;
     }
	
 	
	private String getDataUrl(long videoId){
        String url = getUrlBaseForLocalServer() + "/video/" + videoId + "/data";
        return url;
    }

	
	@RequestMapping(method = RequestMethod.GET, value = VIDEO_SVC_PATH)
	@ResponseBody
	public Collection<Video> getVideoList() {

		// Send a copy of the videos to the client.
		Collection<Video> clientlist = new Vector<Video>();
		Collection<Video> list = videoList.values();

		synchronized (lock) {
			for (Video v : list) {
				clientlist.add(v);
			}
		}

		return clientlist;
	}


	@RequestMapping(method = RequestMethod.POST, value = VIDEO_SVC_PATH)
	@ResponseBody
	public Video addVideo( @RequestBody Video v) {

		synchronized (lock) {
			if (!videoList.containsKey(id)) {
				id++;
				v.setId(id);
				v.setDataUrl(getDataUrl(v.getId()));
				videoList.put(id, v);
				return v;
			}
			else {
				Video existingVideo = videoList.get(id);
	
				existingVideo.setTitle(v.getTitle());
				existingVideo.setDuration(v.getDuration());
				existingVideo.setContentType(v.getContentType());
				existingVideo.setLocation(v.getLocation());
				existingVideo.setSubject(v.getSubject());
				
				return existingVideo;
			}
		}
	}

	
	@ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "Video not found")
	class VideoNotFoundException extends RuntimeException {
		private static final long serialVersionUID = 1L;
	}
	
	
	@ResponseStatus(value = HttpStatus.NOT_FOUND, reason = "Video data not found")
	class VideoDataNotFoundException extends RuntimeException {
		private static final long serialVersionUID = 1L;
	}
	
	
	@RequestMapping(method = RequestMethod.POST, value = VIDEO_DATA_PATH)
	@ResponseBody
	public VideoStatus setVideoData(	@PathVariable(ID_PARAMETER) long id, 
										@RequestPart(DATA_PARAMETER) MultipartFile videoData) {
		
		try {
			
			synchronized (lock) {
				if (!videoList.containsKey(id)) {
					throw new VideoNotFoundException();
				}
				
				Video v = videoList.get(id);
				fileManager.saveVideoData(v, videoData.getInputStream());
				return new VideoStatus(VideoState.READY);
			}

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}

	
	@RequestMapping(method = RequestMethod.GET, value = VIDEO_DATA_PATH)
	@ResponseBody
	public void getVideoData(	@PathVariable("id") long id,
								HttpServletResponse response) {
		
		try {
		
			synchronized (lock) {
				if (!videoList.containsKey(id)) {
					throw new VideoNotFoundException();
				}
				
				Video v = videoList.get(id);
				
				if (!fileManager.hasVideoData(v)) {
					throw new VideoDataNotFoundException();
				}
				
				response.setContentType(v.getContentType());
				fileManager.copyVideoData(v, response.getOutputStream());

			}
		}
		catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}

