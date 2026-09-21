import sys
from pathlib import Path
sys.path.insert(0, str(Path(r'C:\Users\julye\IdeaProjects\vbhelper\tools\digifarm')))
import build_assets
sys.argv = ['build_assets.py', '--', '--source-root', r'C:\Users\julye\AppData\Local\Temp\vbhelper-classic-src', '--output-dir', r'C:\Users\julye\IdeaProjects\vbhelper\build\digifarm-debug\back-assets']
build_assets.main()
import bpy
from mathutils import Vector
scene=bpy.context.scene
bpy.ops.object.camera_add(location=(0,3.4,2.8))
cam=bpy.context.object; cam.rotation_euler=(Vector((0,.22,-.05))-cam.location).to_track_quat('-Z','Y').to_euler(); cam.data.type='ORTHO';cam.data.ortho_scale=2.65;scene.camera=cam
scene.render.resolution_x=800;scene.render.resolution_y=700;scene.render.resolution_percentage=100;scene.render.filepath=r'C:\Users\julye\IdeaProjects\vbhelper\build\digifarm-debug\back-view.png';bpy.ops.render.render(write_still=True)
