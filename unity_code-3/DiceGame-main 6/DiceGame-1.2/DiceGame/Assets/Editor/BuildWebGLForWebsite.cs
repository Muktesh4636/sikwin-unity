#if UNITY_EDITOR
using System.IO;
using System.Linq;
using UnityEditor;
using UnityEditor.Build.Reporting;
using UnityEngine;

/// <summary>
/// CLI: Unity -batchmode -quit -nographics -projectPath ... -executeMethod BuildWebGLForWebsite.Build
/// Output: &lt;project&gt;/Builds/WebGL (then run website/copy-webgl-build.sh)
/// </summary>
public static class BuildWebGLForWebsite
{
    public static void Build()
    {
        var projectRoot = Directory.GetParent(Application.dataPath)!.FullName;
        var outPath = Path.Combine(projectRoot, "Builds", "WebGL");
        if (Directory.Exists(outPath))
            Directory.Delete(outPath, true);
        Directory.CreateDirectory(outPath);

        var scenes = EditorBuildSettings.scenes.Where(s => s.enabled).Select(s => s.path).ToArray();
        if (scenes.Length == 0)
        {
            Debug.LogError("BuildWebGLForWebsite: no scenes in Build Settings.");
            EditorApplication.Exit(1);
            return;
        }

        var opts = new BuildPlayerOptions
        {
            scenes = scenes,
            locationPathName = outPath,
            target = BuildTarget.WebGL,
            options = BuildOptions.None
        };

        var report = BuildPipeline.BuildPlayer(opts);
        if (report.summary.result != BuildResult.Succeeded)
        {
            Debug.LogError("BuildWebGLForWebsite: " + report.summary.result);
            EditorApplication.Exit(1);
            return;
        }

        Debug.Log("BuildWebGLForWebsite: OK → " + outPath);
        EditorApplication.Exit(0);
    }
}
#endif
