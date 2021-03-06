package com.aap.gitst;

import static com.aap.gitst.RepoProperties.META_PROP_ITEM_FILTER;
import static com.aap.gitst.RepoProperties.META_PROP_LAST_PUSH_SHA;
import static com.aap.gitst.RepoProperties.PROP_PASSWORD;
import static com.aap.gitst.RepoProperties.PROP_USER;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import com.aap.gitst.Logger.Level;
import com.aap.gitst.fastexport.Commit;
import com.aap.gitst.fastexport.FastExport;
import com.aap.gitst.fastexport.FastExportException;
import com.starbase.util.OLEDate;

/**
 * @author Andrey Pavlenko
 */
public class Push {

    private final Repo _repo;
    private final Logger _log;

    public Push(final Repo repo) {
        _repo = repo;
        _log = repo.getLogger();
    }

    public static void main(final String[] args) throws FastExportException {
        final Args a = new Args(args);
        final String user = a.get("-u", null);
        final String password = a.get("-p", null);
        final String dir = a.get("-d", null);
        final boolean dryRun = a.hasOption("--dry-run");
        final Level level = a.hasOption("-v") ? Level.DEBUG : a.hasOption("-q")
                ? Level.ERROR : null;
        final Logger log = Logger.createConsoleLogger(level);

        try {
            final Git git = (dir == null) ? new Git() : new Git(new File(dir));
            final RepoProperties props = new RepoProperties(git, "origin");

            if (user != null) {
                props.setSessionProperty(PROP_USER, user);
            }
            if (password != null) {
                props.setSessionProperty(PROP_PASSWORD, password);
            }

            try (final Repo repo = new Repo(props, log)) {
                new Push(repo).push(dryRun);
            }
        } catch (final IllegalArgumentException ex) {
            if (!log.isDebugEnabled()) {
                log.error(ex.getMessage());
            } else {
                log.error(ex.getMessage(), ex);
            }

            printHelp(log);
            System.exit(1);
        } catch (final ExecutionException ex) {
            if (!log.isDebugEnabled()) {
                log.error(ex.getMessage());
            } else {
                log.error(ex.getMessage(), ex);
            }

            System.exit(ex.getExitCode());
        } catch (final Throwable ex) {
            if (!log.isDebugEnabled()) {
                log.error(ex.getMessage());
            } else {
                log.error(ex.getMessage(), ex);
            }

            System.exit(1);
        }
    }

    public Repo getRepo() {
        return _repo;
    }

    public void push() throws IOException, InterruptedException,
            ExecutionException, FastExportException {
        push(null, false);
    }

    public void push(final boolean dryRun) throws IOException,
            InterruptedException, ExecutionException, FastExportException {
        push(null, dryRun);
    }

    public void push(final InputStream in, final boolean dryRun)
            throws IOException, InterruptedException, ExecutionException,
            FastExportException {
        long time = System.currentTimeMillis();
        final Repo repo = getRepo();
        final Git git = repo.getGit();
        final String branch = repo.getBranchName();
        final File exportMarks = repo.createTempFile("export.marks");
        final FastExport exp = new FastExport(repo);
        final Map<Integer, Commit> commits = exp.loadChanges(exportMarks);
        OLEDate startDate = null;

        if (commits.isEmpty()) {
            repo.getLogger().info("No changes found");
        } else if (dryRun) {
            for (final Commit cmt : commits.values()) {
                _log.info(cmt);
                _log.info("--------------------------------------------------------------------------------");
            }
        } else {
            startDate = repo.getServer().getCurrentTime();
            exp.submit(commits);
        }
        if (_log.isInfoEnabled()) {
            time = (System.currentTimeMillis() - time) / 1000;
            _log.info("Total time: "
                    + ((time / 3600) + "h:" + ((time % 3600) / 60) + "m:"
                            + (time % 60) + "s"));
        }
        if (!dryRun && (startDate != null)) {
            final RepoProperties props = repo.getRepoProperties();
            final ItemFilter filter = new ItemFilter(
                    props.getMetaProperty(META_PROP_ITEM_FILTER));
            final OLEDate endDate = repo.getServer().getCurrentTime();
            final int user = repo.getServer().getMyUserAccount().getID();
            filter.add(user, startDate.getDoubleValue(),
                    endDate.getDoubleValue());
            props.setMetaProperty(META_PROP_ITEM_FILTER, filter.toString());
            props.setMetaProperty(META_PROP_LAST_PUSH_SHA, git.showRef(branch));
            props.saveMeta();
            new Marks(exportMarks).store(git.getMarksFile(branch));
        }
    }

    private static void printHelp(final Logger log) {
        log.error("Usage: git st push [-u <user>] [-p password] [-d <directory>] "
                + "[--dry-run] [-v] [-q]");
    }
}
