package ru.forinnyy.tm.component;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.forinnyy.tm.api.repository.ICommandRepository;
import ru.forinnyy.tm.api.repository.IProjectRepository;
import ru.forinnyy.tm.api.repository.ITaskRepository;
import ru.forinnyy.tm.api.repository.IUserRepository;
import ru.forinnyy.tm.api.service.*;
import ru.forinnyy.tm.command.AbstractCommand;
import ru.forinnyy.tm.enumerated.Role;
import ru.forinnyy.tm.enumerated.Status;
import ru.forinnyy.tm.exception.AbstractException;
import ru.forinnyy.tm.exception.entity.AbstractEntityException;
import ru.forinnyy.tm.exception.field.AbstractFieldException;
import ru.forinnyy.tm.exception.system.AbstractSystemException;
import ru.forinnyy.tm.exception.system.ArgumentNotSupportedException;
import ru.forinnyy.tm.exception.system.CommandNotSupportedException;
import ru.forinnyy.tm.exception.user.AbstractUserException;
import ru.forinnyy.tm.model.Project;
import ru.forinnyy.tm.model.Task;
import ru.forinnyy.tm.model.User;
import ru.forinnyy.tm.repository.CommandRepository;
import ru.forinnyy.tm.repository.ProjectRepository;
import ru.forinnyy.tm.repository.TaskRepository;
import ru.forinnyy.tm.repository.UserRepository;
import ru.forinnyy.tm.service.*;
import ru.forinnyy.tm.util.SystemUtil;
import ru.forinnyy.tm.util.TerminalUtil;

import javax.naming.AuthenticationException;
import java.io.File;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Set;


@NoArgsConstructor
public final class Bootstrap implements IServiceLocator {

    @NonNull
    private static final String PACKAGE_COMMANDS = "ru.forinnyy.tm.command";

    @NonNull
    private static final Logger LOGGER_LIFECYCLE = LoggerFactory.getLogger("LIFECYCLE");

    @NonNull
    private static final Logger LOGGER_COMMANDS = LoggerFactory.getLogger("COMMANDS");

    @NonNull
    private final ICommandRepository commandRepository = new CommandRepository();

    @Getter
    @NonNull
    private final ICommandService commandService = new CommandService(commandRepository);

    @NonNull
    private final IProjectRepository projectRepository = new ProjectRepository();

    @Getter
    @NonNull
    private final IProjectService projectService = new ProjectService(projectRepository);

    @NonNull
    private final ITaskRepository taskRepository = new TaskRepository();

    @Getter
    @NonNull
    private final IProjectTaskService projectTaskService = new ProjectTaskService(projectRepository, taskRepository);

    @Getter
    @NonNull
    private final ITaskService taskService = new TaskService(taskRepository);

    @NonNull
    private final IUserRepository userRepository = new UserRepository();

    @Getter
    @NonNull
    private final IPropertyService propertyService = new PropertyService();

    @Getter
    @NonNull
    private final IUserService userService = new UserService(propertyService, userRepository, projectRepository, taskRepository);

    @Getter
    @NonNull
    private final IAuthService authService = new AuthService(propertyService, userService);

    {
        @NonNull final Reflections reflections = new Reflections(PACKAGE_COMMANDS);
        @NonNull final Set<Class<? extends AbstractCommand>> classes =
                reflections.getSubTypesOf(AbstractCommand.class);
        for (@NonNull final Class<? extends AbstractCommand> clazz : classes) registry(clazz);
    }

    @SneakyThrows
    private void registry(@NonNull final Class<? extends AbstractCommand> clazz) {
        if (Modifier.isAbstract(clazz.getModifiers())) return;
        if (!AbstractCommand.class.isAssignableFrom(clazz)) return;
        final AbstractCommand command = clazz.newInstance();
        registry(command);
    }

    private void registry(@NonNull final AbstractCommand command) {
        command.setServiceLocator(this);
        commandService.add(command);
    }

    private void processArgument(final String arg) throws
            AbstractSystemException,
            AbstractEntityException,
            AbstractFieldException,
            AbstractUserException,
            AuthenticationException {
        final AbstractCommand abstractCommand = commandService.getCommandByArgument(arg);
        if (abstractCommand == null) throw new ArgumentNotSupportedException(arg);
        abstractCommand.execute();
    }

    private boolean processArguments(final String[] args) throws
            AbstractSystemException,
            AbstractEntityException,
            AbstractFieldException,
            AbstractUserException,
            AuthenticationException {
        if (args == null || args.length < 1) return false;
        processArgument(args[0]);
        return true;
    }

    private void processCommand(final String command) throws
            AbstractSystemException,
            AbstractFieldException,
            AbstractEntityException,
            AbstractUserException,
            AuthenticationException {
        final AbstractCommand abstractCommand = commandService.getCommandByName(command);
        if (abstractCommand == null) throw new CommandNotSupportedException(command);
        authService.checkRoles(abstractCommand.getRoles());
        abstractCommand.execute();
    }

    @SneakyThrows
    private void initPID() {
        @NonNull final String filename = "task-manager.pid";
        @NonNull final String pid = Long.toString(SystemUtil.getPID());
        Files.write(Paths.get(filename), pid.getBytes());
        @NonNull final File file = new File(filename);
        file.deleteOnExit();
    }

    private void initDemoData() throws AbstractFieldException, AbstractUserException, AbstractEntityException {
        @NonNull final User test = userService.create("test", "test", "test@test.ru");
        @NonNull final User user = userService.create("user", "user", "user@user.ru");
        @NonNull final User admin = userService.create("admin", "admin", Role.ADMIN);

        projectService.add(user.getId(), new Project("USER PROJECT", Status.IN_PROGRESS));
        projectService.add(admin.getId(), new Project("ADMIN PROJECT", Status.NOT_STARTED));

        projectService.add(test.getId(), new Project("TEST PROJECT", Status.IN_PROGRESS));
        projectService.add(test.getId(), new Project("DEMO PROJECT", Status.NOT_STARTED));
        projectService.add(test.getId(), new Project("ALPHA PROJECT", Status.IN_PROGRESS));
        projectService.add(test.getId(), new Project("BETA PROJECT", Status.COMPLETED));

        taskService.add(test.getId(), new Task("MEGA TASK"));
        taskService.add(test.getId(), new Task("BETA TASK"));
    }

    private void initLogger() {
        LOGGER_LIFECYCLE.info("** WELCOME TO TASK-MANAGER **");
        Runtime.getRuntime().addShutdownHook(
                new Thread(() -> LOGGER_LIFECYCLE.info("** TASK-MANAGER IS SHUTTING DOWN **"))
        );
    }

    public void run(final String[] args) throws AbstractException, AuthenticationException {
        if (processArguments(args)) System.exit(0);

        initPID();
        initDemoData();
        initLogger();

        while (!Thread.currentThread().isInterrupted()) {
            try {
                System.out.println("ENTER COMMAND:");
                @NonNull final String command = TerminalUtil.nextLine();
                LOGGER_COMMANDS.info(command);
                processCommand(command);
                System.out.println("[OK]");
            } catch (@NonNull final Exception e) {
                LOGGER_LIFECYCLE.error(e.getMessage());
                System.err.println("[FAIL]");
            }
        }
    }

}
