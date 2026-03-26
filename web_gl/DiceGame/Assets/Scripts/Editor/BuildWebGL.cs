using UnityEditor;
using UnityEngine;

/// <summary>
/// Call from command line: Unity -quit -batchmode -projectPath ... -executeMethod BuildWebGL.BuildFromCommandLine
/// </summary>
public static class BuildWebGL
{
    public static void BuildFromCommandLine()
    {
        if (EditorUserBuildSettings.activeBuildTarget != BuildTarget.WebGL)
        {
            EditorUserBuildSettings.SwitchActiveBuildTarget(BuildTargetGroup.WebGL, BuildTarget.WebGL);
        }
        string buildPath = "Builds/WebGL";
        BuildPlayerOptions opts = new BuildPlayerOptions
        {
            scenes = GetEnabledScenePaths(),
            locationPathName = buildPath,
            target = BuildTarget.WebGL,
            options = BuildOptions.None
        };
        var report = BuildPipeline.BuildPlayer(opts);
        if (report.summary.result != UnityEditor.Build.Reporting.BuildResult.Succeeded)
        {
            throw new System.Exception("WebGL build failed: " + report.summary.result);
        }
    }

    static string[] GetEnabledScenePaths()
    {
        var scenes = new System.Collections.Generic.List<string>();
        foreach (var s in EditorBuildSettings.scenes)
        {
            if (s.enabled && !string.IsNullOrEmpty(s.path))
                scenes.Add(s.path);
        }
        return scenes.ToArray();
    }
}
