package ru.forinnyy.tm.command.system;


import lombok.NonNull;

public final class ApplicationAboutCommand extends AbstractSystemCommand {

    @NonNull
    private static final String DESCRIPTION = "Show developer info";

    @NonNull
    private static final String NAME = "about";

    @NonNull
    private static final String ARGUMENT = "-a";

    @Override
    public void execute() {
        System.out.println("[ABOUT]");
        System.out.println("AUTHOR: " + getPropertyService().getAuthorName());
        System.out.println("E-MAIL: " + getPropertyService().getAuthorEmail());
        System.out.println();

        System.out.println("[GIT]");
        System.out.println("BRANCH: " + getPropertyService().getGitBranch());
        System.out.println("COMMIT ID: " + getPropertyService().getGitCommitId());
        System.out.println("COMMITTER: " + getPropertyService().getGitCommitterName());
        System.out.println("E-MAIL: " + getPropertyService().getGitCommitterEmail());
        System.out.println("MESSAGE: " + getPropertyService().getGitCommitMessage());
        System.out.println("TIME: " + getPropertyService().getGitCommitTime());
    }

    @NonNull
    @Override
    public String getArgument() {
        return ARGUMENT;
    }

    @NonNull
    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @NonNull
    @Override
    public String getName() {
        return NAME;
    }

}
