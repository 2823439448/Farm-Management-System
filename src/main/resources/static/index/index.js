const MAX_DATA_POINTS = 60; // 存储60分钟（1小时）的数据

// 模拟实时数据
let timeLabels = [];
let tempData = [];
let humidityData = [];

// 初始化图表
const ctx = document.getElementById('tempHumidityChart').getContext('2d');
const chart = new Chart(ctx, {
    type: 'line',
    data: {
        labels: timeLabels,
        datasets: [
            {
                label: '温度 (℃)',
                data: tempData,
                borderColor: 'rgb(255, 99, 132)',
                backgroundColor: 'rgba(255, 99, 132, 0.2)',
                yAxisID: 'yTemp',
                fill: true,
                tension: 0.1 // 使曲线更平滑
            },
            {
                label: '湿度 (%)',
                data: humidityData,
                borderColor: 'rgb(54, 162, 235)',
                backgroundColor: 'rgba(54, 162, 235, 0.2)',
                yAxisID: 'yHumid',
                fill: true,
                tension: 0.1
            }
        ]
    },
    options: {
        responsive: true,
        maintainAspectRatio: false, // 允许图表自由伸缩
        scales: {
            yTemp: {
                type: 'linear',
                display: true,
                position: 'left',
                title: { display: true, text: '温度 (℃)' },
                suggestedMin: 20, suggestedMax: 35
            },
            yHumid: {
                type: 'linear',
                display: true,
                position: 'right',
                title: { display: true, text: '湿度 (%)' },
                suggestedMin: 40, suggestedMax: 80,
                grid: { drawOnChartArea: false } // 仅绘制左侧网格线
            }
        },
        plugins: {
            tooltip: {
                mode: 'index',
                intersect: false,
            }
        },
        hover: {
            mode: 'nearest',
            intersect: true
        }
    }
});

// 模拟数据生成函数
function fetchData() {
    const now = new Date();
    // 修正: 确保时间字符串格式正确（分:秒）
    const timeString = `${now.getMinutes() < 10 ? '0' : ''}${now.getMinutes()}:${now.getSeconds() < 10 ? '0' : ''}${now.getSeconds()}`;

    // 模拟新的温度、湿度和 pH 值
    const newTemp = (Math.random() * 3 + 24).toFixed(1);
    const newHumid = (Math.random() * 10 + 60).toFixed(0);
    // ***** 修正 1: 模拟 pH 值 (例如 5.5 到 7.5 之间，保留一位小数) *****
    const newPH = (Math.random() * 2 + 5.5).toFixed(1);

    // 更新图表数据
    timeLabels.push(timeString);
    tempData.push(newTemp);
    humidityData.push(newHumid);

    // 保持数据点数量不超过 MAX_DATA_POINTS
    if (timeLabels.length > MAX_DATA_POINTS) {
        timeLabels.shift();
        tempData.shift();
        humidityData.shift();
    }

    // 更新实时数值显示
    document.getElementById('currentTemp').textContent = newTemp;
    document.getElementById('currentHumid').textContent = newHumid;
    // ***** 修正 2: 更改 ID 'currentLight' 为 'currentPh' *****
    document.getElementById('currentPh').textContent = newPH;

    // 重新渲染图表
    chart.update();
}

// 每隔 5 秒获取一次数据（演示效果）
setInterval(fetchData, 5000);

// 初始化加载第一批数据
fetchData();


// --- 交互事件处理 ---

// 浇水按钮事件
document.getElementById('waterBtn').addEventListener('click', () => {
    alert('✅ 浇水指令已发送！预计 2 分钟内完成。');
    console.log('发送浇水指令...');
});

// 升温按钮事件
document.getElementById('heatBtn').addEventListener('click', () => {
    const target = document.getElementById('targetTemp').value;
    if (target && !isNaN(target)) {
        alert(`🔥 升温指令已发送！目标温度设定为 ${target}℃。`);
        console.log(`发送升温指令，目标 ${target}℃...`);
    } else {
        alert('请输入有效的目标温度！');
    }
});


// --- 天气预报功能 ---

// ⚠️ 请将 'YOUR_API_KEY' 替换为您自己的 OpenWeatherMap API Key
const API_KEY = '07f1b15756b74cfdb9c135254252511';
const DEFAULT_CITY = '成都';


async function fetchWeather(city) {
    const weatherInfoDiv = document.getElementById('weatherInfo');

    if (!API_KEY || API_KEY === "YOUR_API_KEY") {
        weatherInfoDiv.innerHTML = '<p style="color:red;">⚠️ 请先填写 WeatherAPI Key！</p>';
        return;
    }

    const apiUrl = `https://api.weatherapi.com/v1/current.json?key=${API_KEY}&q=${city}&lang=zh`;

    try {
        weatherInfoDiv.innerHTML = `<p>正在查询 ${city} 的天气...</p>`;

        const response = await fetch(apiUrl);
        if (!response.ok) {
            throw new Error(`无法获取 ${city} 的天气，请检查城市名称`);
        }

        const data = await response.json();
        displayWeather(data);

    } catch (error) {
        weatherInfoDiv.innerHTML = `<p style="color:red;">错误：${error.message}</p>`;
        console.error("WeatherAPI 获取失败：", error);
    }
}

// 显示 WeatherAPI 天气数据
function displayWeather(data) {
    const weatherInfoDiv = document.getElementById('weatherInfo');

    const temp = data.current.temp_c;
    const description = data.current.condition.text;
    const iconUrl = "https:" + data.current.condition.icon; // WeatherAPI 返回 //cdn… 需要补 https:

    weatherInfoDiv.innerHTML = `
        <p>
            <img src="${iconUrl}" class="weather-icon">
            <strong>${data.location.name}</strong>（实时）
        </p>
        <p>🌡️ 温度：<strong>${temp} ℃</strong></p>
        <p>☁️ 描述：${description}</p>
        <p>💧 湿度：${data.current.humidity} %</p>
        <p>💨 风速：${data.current.wind_kph} km/h</p>
    `;
}

// 默认加载
document.getElementById('cityInput').value = DEFAULT_CITY;
fetchWeather(DEFAULT_CITY);

// 查询按钮
document.getElementById('getWeatherBtn').addEventListener('click', () => {
    const city = document.getElementById('cityInput').value.trim();
    if (city) fetchWeather(city);
});


// --- 新增: 接收 AI 指令并自动执行升温操作 ---
// 此逻辑在所有 DOM 和 Chart 初始化完成后执行

function checkAICommand() {
    // 获取 chat.html 中存储的值和标志
    const aiSetTemp = localStorage.getItem("aiSetTemp");
    const aiAutoHeat = localStorage.getItem("aiAutoHeat");

    // 检查是否有 AI 设定的温度和自动加热的标志
    if (aiSetTemp && aiAutoHeat === "true") {

        // 1. 设置目标温度输入框的值
        const targetTempInput = document.getElementById('targetTemp');
        // 确保值有效
        if (!isNaN(parseFloat(aiSetTemp))) {
            targetTempInput.value = parseFloat(aiSetTemp);
        } else {
            console.error("AI 设定的温度值无效:", aiSetTemp);
            // 即使值无效，也要清理标志，防止无限循环
            localStorage.removeItem("aiSetTemp");
            localStorage.removeItem("aiAutoHeat");
            return;
        }

        // 2. 模拟点击“升温至目标”按钮
        const heatBtn = document.getElementById('heatBtn');

        // 延迟执行点击和清除操作，给用户一个缓冲时间
        setTimeout(() => {
            // 触发点击事件，执行 'heatBtn' 的事件监听器
            heatBtn.click();

            // 3. 清除 localStorage 中的值和标志，防止重复执行
            localStorage.removeItem("aiSetTemp");
            localStorage.removeItem("aiAutoHeat");

            console.log(`AI助手指令已执行：目标温度设置为 ${aiSetTemp}℃ 并发送升温指令。`);

        }, 500); // 延迟 0.5 秒
    }
}

// 页面加载完成后立即检查 AI 指令
checkAICommand();