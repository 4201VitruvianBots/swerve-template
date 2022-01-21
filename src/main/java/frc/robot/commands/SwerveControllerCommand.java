package frc.robot.commands;

import static edu.wpi.first.wpilibj.util.ErrorMessages.requireNonNullParam;

import java.util.function.Consumer;
import java.util.function.Supplier;

import edu.wpi.first.math.controller.HolonomicDriveController;
import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.math.controller.ProfiledPIDController;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.math.kinematics.SwerveDriveKinematics;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.math.trajectory.Trajectory;
import edu.wpi.first.wpilibj.Timer;

import edu.wpi.first.wpilibj2.command.CommandBase;
import frc.robot.utils.PathPlannerTrajectory.PathPlannerState;


public class SwerveControllerCommand extends CommandBase{
    private final Timer m_timer = new Timer();
    private final Trajectory m_trajectory;
    private final Supplier<Pose2d> m_pose;
    private final SwerveDriveKinematics m_kinematics;
    private final HolonomicDriveController m_controller;
    private final Consumer<SwerveModuleState[]> m_outputModuleStates;
    private final Supplier<Rotation2d> m_desiredRotation;

    /**
     Constructs a new SwerveControllerCommand that when executed will follow the provided
     trajectory. This command will not return output voltages but rather raw module states from the
     position controllers which need to be put into a velocity PID.

     <p>Note: The controllers will not set the outputVolts to zero upon completion of the path-
     this is left to the user, since it is not appropriate for paths with nonstationary endstates.

     @param trajectory The trajectory to follow.
     @param pose A function that supplies the robot pose - use one of the odometry classes to
         provide this.
     @param kinematics The kinematics for the robot drivetrain.
     @param xController The Trajectory Tracker PID controller for the robot's x position.
     @param yController The Trajectory Tracker PID controller for the robot's y position.
     @param thetaController The Trajectory Tracker PID controller for angle for the robot.
     @param desiredRotation The angle that the drivetrain should be facing. This is sampled at each
         time step.
     @param outputModuleStates The raw output module states from the position controllers.
     @param requirements The subsystems to require.
    */
    @SuppressWarnings("ParameterName")
    public SwerveControllerCommand(
      Trajectory trajectory,
      Supplier<Pose2d> pose,
      SwerveDriveKinematics kinematics,
      PIDController xController,
      PIDController yController,
      ProfiledPIDController thetaController,
      Supplier<Rotation2d> desiredRotation,
      Consumer<SwerveModuleState[]> outputModuleStates) {

        m_trajectory = requireNonNullParam(trajectory, "trajectory", "SwerveControllerCommand");
        m_pose = requireNonNullParam(pose, "pose", "SwerveControllerCommand");
        m_kinematics = requireNonNullParam(kinematics, "kinematics", "SwerveControllerCommand");

        m_controller =
            new HolonomicDriveController(
                requireNonNullParam(xController, "xController", "SwerveControllerCommand"),
                requireNonNullParam(yController, "xController", "SwerveControllerCommand"),
                requireNonNullParam(thetaController, "thetaController", "SwerveControllerCommand"));

        m_outputModuleStates =
            requireNonNullParam(outputModuleStates, "frontLeftOutput", "SwerveControllerCommand");

        m_desiredRotation =
            requireNonNullParam(desiredRotation, "desiredRotation", "SwerveControllerCommand");

    }

    public SwerveControllerCommand(
      Trajectory trajectory,
      Supplier<Pose2d> pose,
      SwerveDriveKinematics kinematics,
      PIDController xController,
      PIDController yController,
      ProfiledPIDController thetaController,
      Consumer<SwerveModuleState[]> outputModuleStates) {
        this(
            trajectory,
            pose,
            kinematics,
            xController,
            yController,
            thetaController,
            () ->
                trajectory.getStates().get(trajectory.getStates().size() - 1).poseMeters.getRotation(),
            outputModuleStates);
    }

    @Override
    public void initialize() {
        m_timer.reset();
        m_timer.start();
    }

    @Override
    public void execute() {
        double currentTime = m_timer.get();

        var desiredState = (PathPlannerState) m_trajectory.sample(currentTime);
        Rotation2d rotation =  desiredState.holonomicRotation;

        ChassisSpeeds targetChassisSpeeds = m_controller.calculate(m_pose.get(), desiredState, m_desiredRotation.get());
        var moduleStates = m_kinematics.toSwerveModuleStates(targetChassisSpeeds);

        m_outputModuleStates.accept(moduleStates);
    }

    @Override
    public void end(boolean interrupted) {
      m_timer.stop();
    }
  
    @Override
    public boolean isFinished() {
      return m_timer.hasElapsed(m_trajectory.getTotalTimeSeconds());
    }
}
