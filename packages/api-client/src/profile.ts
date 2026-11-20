import type { UserProfileUpdate } from "./types";

export const USER_PROFILE_FIELDS = [
  { key: "nickname", label: "昵称", max: 64 },
  { key: "realName", label: "真实姓名", max: 32 },
  { key: "phone", label: "手机号", max: 11 },
  { key: "college", label: "学院", max: 64 },
  { key: "campusArea", label: "校区", max: 64 },
  { key: "dormBuilding", label: "宿舍楼", max: 64 },
] as const;

export async function prepareAvatar(file: File): Promise<File> {
  if (!["image/jpeg", "image/png", "image/webp"].includes(file.type)) {
    throw new Error("请选择 JPG、PNG 或 WebP 图片");
  }
  if (file.size === 0 || file.size > 5 * 1024 * 1024) {
    throw new Error("头像图片不能为空且不能超过 5MB");
  }
  let image: ImageBitmap;
  try {
    image = await createImageBitmap(file);
  } catch {
    throw new Error("图片无法读取，请选择其他图片");
  }
  try {
    if (image.width > 8192 || image.height > 8192) {
      throw new Error("图片尺寸过大，请选择较小的图片");
    }
    const canvas = document.createElement("canvas");
    canvas.width = 256;
    canvas.height = 256;
    const context = canvas.getContext("2d");
    if (!context) throw new Error("当前浏览器无法处理图片");
    context.fillStyle = "#fff";
    context.fillRect(0, 0, 256, 256);
    const side = Math.min(image.width, image.height);
    context.drawImage(image, (image.width - side) / 2, (image.height - side) / 2, side, side, 0, 0, 256, 256);
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, "image/jpeg", 0.9));
    if (!blob) throw new Error("图片处理失败，请重新选择");
    return new File([blob], "avatar.jpg", { type: "image/jpeg" });
  } finally {
    image.close();
  }
}

export function profileChanges(form: Required<UserProfileUpdate>, original: Required<UserProfileUpdate>): UserProfileUpdate {
  const body: UserProfileUpdate = {};
  for (const field of USER_PROFILE_FIELDS) {
    const value = form[field.key].trim();
    if ([...value].length > field.max) {
      throw new Error(`${field.label}不能超过${field.max}字`);
    }
    if (field.key === "phone" && value !== "" && !/^1[3-9]\d{9}$/.test(value)) {
      throw new Error("请填写正确的11位手机号，或留空");
    }
    if (value !== original[field.key]) body[field.key] = value;
  }
  if (Object.keys(body).length === 0) body.nickname = form.nickname.trim();
  return body;
}
