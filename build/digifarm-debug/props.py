import bpy
bpy.ops.wm.read_factory_settings(use_empty=True)
m=bpy.data.materials.new('x')
print('surface',hasattr(m,'surface_render_method'),getattr(m,'surface_render_method',None))
print('blend',hasattr(m,'blend_method'),getattr(m,'blend_method',None))
print('transparent',hasattr(m,'use_transparency_overlap'),getattr(m,'use_transparency_overlap',None))
