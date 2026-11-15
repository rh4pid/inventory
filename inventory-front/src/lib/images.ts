/**
 * Shrinks a picture in the browser before upload: longest side at most `max` px, saved as JPEG.
 * A phone photo of several MB becomes ~30-80 KB, which is plenty for product tiles.
 */
export async function shrinkImage(file: File, max = 640, quality = 0.82): Promise<Blob> {
  if (!file.type.startsWith("image/")) throw new Error("Choose a picture (JPG, PNG or WebP)");
  const url = URL.createObjectURL(file);
  try {
    const img = await new Promise<HTMLImageElement>((resolve, reject) => {
      const i = new Image();
      i.onload = () => resolve(i);
      i.onerror = () => reject(new Error("That file couldn't be opened as a picture"));
      i.src = url;
    });
    const scale = Math.min(1, max / Math.max(img.naturalWidth, img.naturalHeight));
    const w = Math.max(1, Math.round(img.naturalWidth * scale));
    const h = Math.max(1, Math.round(img.naturalHeight * scale));
    const canvas = document.createElement("canvas");
    canvas.width = w;
    canvas.height = h;
    const ctx = canvas.getContext("2d");
    if (!ctx) throw new Error("Your browser couldn't process the picture");
    ctx.fillStyle = "#ffffff"; // transparent PNGs get a white background instead of black
    ctx.fillRect(0, 0, w, h);
    ctx.drawImage(img, 0, 0, w, h);
    const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, "image/jpeg", quality));
    if (!blob) throw new Error("Your browser couldn't process the picture");
    return blob;
  } finally {
    URL.revokeObjectURL(url);
  }
}
