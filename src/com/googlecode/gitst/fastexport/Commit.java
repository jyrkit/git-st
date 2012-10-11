package com.googlecode.gitst.fastexport;

import static com.googlecode.gitst.Repo.LS;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import com.googlecode.gitst.Logger;
import com.googlecode.gitst.Logger.ProgressBar;
import com.googlecode.gitst.RemoteFile;
import com.googlecode.gitst.Repo;
import com.starbase.starteam.CheckinEvent;
import com.starbase.starteam.CheckinListener;
import com.starbase.starteam.CheckinManager;
import com.starbase.starteam.File;
import com.starbase.starteam.ItemList;

/**
 * @author Andrey Pavlenko
 */
public class Commit implements FastExportCommand {
    private final List<FileChange> _changes = new ArrayList<>();
    private Integer _mark;
    private Integer _from;
    private long _date;
    private String _committer;
    private String _comment = "";
    private boolean _committed;

    public Integer getMark() {
        return _mark;
    }

    public void setMark(final Integer mark) {
        _mark = mark;
    }

    public Integer getFrom() {
        return _from;
    }

    public void setFrom(final Integer from) {
        _from = from;
    }

    public long getDate() {
        return _date;
    }

    public void setDate(final long date) {
        _date = date;
    }

    public void setDate(final String date, final String shift) {
        final long d = Long.parseLong(date) * 1000;
        final int off = TimeZone.getDefault().getOffset(d)
                - TimeZone.getTimeZone("GMT" + shift).getOffset(d);
        _date = d + off;
    }

    public String getCommitter() {
        return _committer;
    }

    public void setCommitter(final String committer) {
        _committer = committer;
    }

    public String getComment() {
        return _comment;
    }

    public void setComment(final String comment) {
        _comment = comment;
    }

    public List<FileChange> getChanges() {
        return _changes;
    }

    public void addChange(final FileChange c) {
        getChanges().add(c);
    }

    public synchronized boolean exec(final Repo repo) throws IOException,
            FastExportException {
        final List<FileChange> changes = getChanges();

        if (!_committed && !changes.isEmpty()) {
            final CheckinManager mgr = repo.createCheckinManager(getComment());
            final Map<RemoteFile, FileModify> mod = new LinkedHashMap<>();
            final Logger log = repo.getLogger();

            if (log.isInfoEnabled()) {
                log.info(this);
                log.info("--------------------------------------------------------------------------------");
            }
            for (final FileChange c : changes) {
                if (c instanceof FileModify) {
                    final FileModify m = (FileModify) c;
                    final File f = m.getFile(repo);
                    mod.put(new RemoteFile(f), m);
                } else {
                    c.exec(repo, this);
                }
            }
            if (!mod.isEmpty()) {
                commit(mgr, repo, mod);
            }

            return _committed = true;
        } else {
            return false;
        }
    }

    private void commit(final CheckinManager mgr, final Repo repo,
            final Map<RemoteFile, FileModify> changes) throws IOException {
        final ItemList items = new ItemList();

        for (final FileModify c : changes.values()) {
            final File f = c.getFile(repo);
            items.addItem(f);
        }

        final ProgressBar pb = repo.getLogger().createProgressBar(
                "Checking in", items.size());
        final Listener l = new Listener(repo, changes, pb);
        mgr.addCheckinListener(l);
        mgr.checkin(items);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        final String comment = getComment();
        sb.append(new SimpleDateFormat(Repo.DATE_FORMAT).format(getDate()));
        sb.append(' ');
        sb.append(getCommitter()).append(LS);
        sb.append(comment);

        if (!comment.endsWith("\n")) {
            sb.append(LS);
        }
        for (final FileChange c : getChanges()) {
            sb.append(LS).append(c);
        }

        return sb.toString();
    }

    private final class Listener implements CheckinListener {
        private final Repo _repo;
        private final Map<RemoteFile, FileModify> _changes;
        private final ProgressBar _pb;

        public Listener(final Repo repo,
                final Map<RemoteFile, FileModify> changes, final ProgressBar pb) {
            _repo = repo;
            _changes = changes;
            _pb = pb;
        }

        @Override
        public void onStartFile(final CheckinEvent e) {
            try {
                final File f = e.getCurrentFile();
                e.setCurrentWorkingFile(_changes.get(new RemoteFile(f))
                        .getLocalFile(_repo));
            } catch (final IOException ex) {
                throw new RuntimeException(ex);
            }
        }

        @Override
        public void onNotifyProgress(final CheckinEvent e) {
            if (e.isFinished()) {
                _pb.done(1);
                e.getCurrentWorkingFile().delete();
            }
        }
    }
}
