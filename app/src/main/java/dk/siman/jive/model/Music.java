/*
 * Copyright (c) 2015. Anders Nielsen
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package dk.siman.jive.model;


/**
 * Represents a single audio file on the Android system.
 *
 * It's a simple data container, filled with setters/getters.
 *
 * Only mandatory fields are:
 * - id (which is a unique Android identified for a media file
 *       anywhere on the system)
 * - filePath (full path for the file on the filesystem).
 */
class Music {

	private final long id;
	private final String filePath;

	/**
	 * Creates a new Song, with specified `songID` and `filePath`.
	 *
	 * @note It's a unique Android identifier for a media file
	 *       anywhere on the system.
	 */
	public Music(long id, String filePath) {
		this.id        = id;
		this.filePath  = filePath;
	}

	/**
	 * Identifier for the song on the Android system.
	 * (so we can locate the file anywhere)
	 */
	public long getId() {
		return id;
	}

	/**
	 * Full path for the music file within the filesystem.
	 */
	public String getFilePath() {
		return filePath;
	}

	// optional metadata

    private String songid      = "";
	private String title       = "";
	private String artist      = "";
	private String album       = "";
	private String albumartist = "";
    private long   albumid     = -1;
	private int    year        = -1;
	private String genre       = "";
	private int    track_no    = -1;
	private long   duration_ms = -1;


    public String getSongId() {
        return songid;
    }
    public void setSongId(String songid) {
        this.songid = songid;
    }

	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}

	public String getArtist() {
		return artist;
	}
	public void setArtist(String artist) {
		this.artist = artist;
	}

	public String getAlbum() {
		return album;
	}
	public void setAlbum(String album) {
		this.album = album;
	}

	public String getAlbumArtist() {
		return albumartist;
	}
	public void setAlbumArtist(String albumartist) {
		this.albumartist = albumartist;
	}

    public Long getAlbumId() {
        return albumid;
    }
    public void setAlbumId(Long albumid) {
        this.albumid = albumid;
    }

	public int getYear() {
		return year;
	}
	public void setYear(int year) {
		this.year = year;
	}

	public String getGenre() {
		return genre;
	}
	public void setGenre(String genre) {
		this.genre = genre;
	}

	public int getTrackNumber() {
		return track_no;
	}
	public void setTrackNumber(int track_no) {
		this.track_no = track_no;
	}

	/**
	 * Sets the duration of the song, in miliseconds.
	 */
	public void setDuration(long duration_ms) {
		this.duration_ms = duration_ms;
	}
	/**
	 * Returns the duration of the song, in miliseconds.
	 */
	public long getDuration() {
		return duration_ms;
	}
	private long getDurationSeconds() {
		return getDuration() / 1000;
	}
	public long getDurationMinutes() {
		return getDurationSeconds() / 60;
	}
}
